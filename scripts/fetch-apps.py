#!/usr/bin/env python3
"""Downloads and verifies the prebuilt apps listed in apps/manifest.yml and
regenerates apps/Android.bp and apps/apps.mk so they are imported into the
build and added to the product. The build itself never touches the network;
run this script first, and the product config fails if an APK is missing.

The manifest holds a list of apps. Each app has these keys:

  name           Soong module name, also used as the APK file name (required)
  url            https URL of the APK (required)
  sha256         SHA-256 of the APK file (required)
  signing_certs  SHA-256 digests of every expected signing certificate (required)
  partition      system (default), system_ext, product or vendor
  privileged     install as a privileged app (default false)
  hidden_api_whitelisted  exempt the app from hidden API restrictions (default false)
  overrides      names of modules this app replaces (default none)

Everything else comes from the APK's own manifest through aapt2. Libraries it
declares with <uses-library> are passed to Soong, which otherwise fails its
uses-library check. For a privileged app, every permission it requests that the
platform protects as privileged is written to a privapp permissions allowlist.

Every APK must match its checksum and carry exactly the pinned signing
certificates, otherwise nothing is written.
"""

import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.exit("PyYAML is required: pip install pyyaml")

ROOT = Path(__file__).resolve().parent.parent
TOP = ROOT.parent.parent
APPS_DIR = ROOT / "apps"
MANIFEST = APPS_DIR / "manifest.yml"
BLUEPRINT = APPS_DIR / "Android.bp"
MAKEFILE = APPS_DIR / "apps.mk"
DOWNLOAD_DIR = APPS_DIR / "prebuilt"
ETC_DIR = APPS_DIR / "etc"
PLATFORM_MANIFEST = TOP / "frameworks" / "base" / "core" / "res" / "AndroidManifest.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

PARTITION_PROPERTIES = {
    "system": None,
    "system_ext": "system_ext_specific",
    "product": "product_specific",
    "vendor": "soc_specific",
}
MODULE_NAME = re.compile(r"^[A-Za-z0-9_.-]+$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
PACKAGE_NAME = re.compile(r"^package: name='([^']+)'", re.M)
REQUESTED_PERMISSION = re.compile(r"^uses-permission: name='([^']+)'", re.M)
USES_LIBRARY = re.compile(r"^uses-library(-not-required)?:'([^']+)'$", re.M)
SIGNER_DIGEST = re.compile(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})$", re.M)
DOWNLOAD_TIMEOUT_SECONDS = 60
CHUNK_BYTES = 1 << 20


class ManifestError(Exception):
    pass


class VerificationError(Exception):
    pass


@dataclass(frozen=True)
class ApkInfo:
    package: str
    required_libraries: tuple
    optional_libraries: tuple
    permissions: tuple


@dataclass(frozen=True)
class EtcFile:
    module_name: str
    filename: str
    sub_dir: str
    content: str

    @property
    def path(self):
        return ETC_DIR / self.filename


@dataclass(frozen=True)
class App:
    name: str
    url: str
    sha256: str
    signing_certs: frozenset
    partition: str
    privileged: bool
    hidden_api_whitelisted: bool
    overrides: tuple

    @property
    def apk_path(self):
        return DOWNLOAD_DIR / f"{self.name}.apk"


def load_apps(manifest_path):
    """Parses the manifest into a list of validated apps."""
    document = yaml.safe_load(manifest_path.read_text()) or {}
    entries = document.get("apps") or []
    apps = [parse_app(entry) for entry in entries]
    names = [app.name for app in apps]
    if len(set(names)) != len(names):
        raise ManifestError("duplicate app names in manifest")
    return apps


def parse_app(entry):
    name = require_string(entry, "name")
    if not MODULE_NAME.match(name):
        raise ManifestError(f"invalid app name: {name!r}")
    url = require_string(entry, "url")
    if not url.startswith("https://"):
        raise ManifestError(f"{name}: url must use https")
    partition = entry.get("partition", "system")
    if partition not in PARTITION_PROPERTIES:
        raise ManifestError(f"{name}: unknown partition {partition!r}")
    return App(
        name=name,
        url=url,
        sha256=require_digest(entry, name, "sha256"),
        signing_certs=parse_signing_certs(entry, name),
        partition=partition,
        privileged=bool(entry.get("privileged", False)),
        hidden_api_whitelisted=bool(entry.get("hidden_api_whitelisted", False)),
        overrides=tuple(entry.get("overrides") or ()),
    )


def require_string(entry, key):
    value = entry.get(key)
    if not isinstance(value, str) or not value:
        raise ManifestError(f"app is missing required key {key!r}: {entry}")
    return value


def require_digest(entry, name, key):
    value = require_string(entry, key).lower()
    if not SHA256.match(value):
        raise ManifestError(f"{name}: {key} must be 64 hex characters")
    return value


def parse_signing_certs(entry, name):
    certs = entry.get("signing_certs")
    if not certs or not isinstance(certs, list):
        raise ManifestError(f"{name}: signing_certs must be a non-empty list")
    digests = frozenset(str(cert).lower().replace(":", "") for cert in certs)
    if not all(SHA256.match(digest) for digest in digests):
        raise ManifestError(f"{name}: signing_certs must be SHA-256 hex digests")
    return digests


def file_sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(CHUNK_BYTES):
            digest.update(chunk)
    return digest.hexdigest()


def download(url, destination):
    with urllib.request.urlopen(url, timeout=DOWNLOAD_TIMEOUT_SECONDS) as response:
        with destination.open("wb") as output:
            shutil.copyfileobj(response, output, CHUNK_BYTES)


def find_apksigner():
    configured = os.environ.get("APKSIGNER") or shutil.which("apksigner")
    if configured:
        return [configured]
    host = "linux" if sys.platform.startswith("linux") else "darwin"
    bundled_jar = TOP / "prebuilts" / "sdk" / "tools" / host / "lib" / "apksigner.jar"
    if not bundled_jar.exists():
        raise VerificationError("apksigner not found; set APKSIGNER")
    return ["java", "-jar", str(bundled_jar)]


def find_aapt2():
    configured = os.environ.get("AAPT2") or shutil.which("aapt2")
    if configured:
        return configured
    host = "linux" if sys.platform.startswith("linux") else "darwin"
    bundled = TOP / "prebuilts" / "sdk" / "tools" / host / "bin" / "aapt2"
    if not bundled.exists():
        raise VerificationError("aapt2 not found; set AAPT2")
    return str(bundled)


def read_apk_info(apk, aapt2):
    result = subprocess.run(
        [aapt2, "dump", "badging", str(apk)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise VerificationError(f"{apk.name}: aapt2 failed: {result.stderr.strip()}")
    package = PACKAGE_NAME.search(result.stdout)
    if not package:
        raise VerificationError(f"{apk.name}: no package name in manifest")
    required, optional = [], []
    for not_required, library in USES_LIBRARY.findall(result.stdout):
        (optional if not_required else required).append(library)
    return ApkInfo(
        package=package.group(1),
        required_libraries=tuple(required),
        optional_libraries=tuple(optional),
        permissions=tuple(REQUESTED_PERMISSION.findall(result.stdout)),
    )


def load_privileged_permissions():
    root = ET.parse(PLATFORM_MANIFEST).getroot()
    return {
        permission.get(ANDROID_NS + "name")
        for permission in root.iter("permission")
        if "privileged" in permission.get(ANDROID_NS + "protectionLevel", "")
    }


def read_signing_certs(apk, apksigner):
    result = subprocess.run(
        [*apksigner, "verify", "--print-certs", str(apk)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise VerificationError(f"{apk.name}: apksigner rejected the APK: {result.stderr.strip()}")
    return frozenset(SIGNER_DIGEST.findall(result.stdout))


def verify_apk(app, apk, apksigner):
    actual_sha256 = file_sha256(apk)
    if actual_sha256 != app.sha256:
        raise VerificationError(
            f"{app.name}: checksum mismatch, expected {app.sha256}, got {actual_sha256}"
        )
    actual_certs = read_signing_certs(apk, apksigner)
    if actual_certs != app.signing_certs:
        raise VerificationError(
            f"{app.name}: signing certificates mismatch, "
            f"expected {sorted(app.signing_certs)}, got {sorted(actual_certs)}"
        )


def is_up_to_date(app, apksigner):
    if not app.apk_path.exists():
        return False
    try:
        verify_apk(app, app.apk_path, apksigner)
    except VerificationError:
        return False
    return True


def fetch_app(app, apksigner):
    if is_up_to_date(app, apksigner):
        print(f"{app.name}: up to date")
        return
    print(f"{app.name}: downloading {app.url}")
    with tempfile.NamedTemporaryFile(dir=DOWNLOAD_DIR, suffix=".part", delete=False) as partial:
        partial_path = Path(partial.name)
    try:
        download(app.url, partial_path)
        verify_apk(app, partial_path, apksigner)
        partial_path.replace(app.apk_path)
    finally:
        partial_path.unlink(missing_ok=True)


def prune_unlisted_apks(apps):
    wanted = {app.apk_path for app in apps}
    for stale in DOWNLOAD_DIR.glob("*.apk"):
        if stale not in wanted:
            print(f"removing unlisted {stale.name}")
            stale.unlink()


def render_string_list(values):
    return ", ".join(f'"{value}"' for value in values)


def render_privapp_allowlist(package, permissions):
    entries = "\n".join(f'        <permission name="{name}"/>' for name in permissions)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<permissions>\n"
        f'    <privapp-permissions package="{package}">\n'
        f"{entries}\n"
        "    </privapp-permissions>\n"
        "</permissions>\n"
    )


def render_hidden_api_whitelist(package):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<config>\n"
        f'    <hidden-api-whitelisted-app package="{package}"/>\n'
        "</config>\n"
    )


def build_etc_files(app, info, privileged_permissions):
    files = []
    allowed = sorted(set(info.permissions) & privileged_permissions)
    if app.privileged and allowed:
        files.append(EtcFile(
            f"{app.name}-privapp-permissions",
            f"privapp-permissions-{info.package}.xml",
            "permissions",
            render_privapp_allowlist(info.package, allowed),
        ))
    if app.hidden_api_whitelisted:
        files.append(EtcFile(
            f"{app.name}-hiddenapi-whitelist",
            f"{info.package}-hiddenapi-package-whitelist.xml",
            "sysconfig",
            render_hidden_api_whitelist(info.package),
        ))
    return files


def partition_lines(app):
    partition_property = PARTITION_PROPERTIES[app.partition]
    return [f"    {partition_property}: true,"] if partition_property else []


def render_etc_module(app, etc_file):
    lines = [
        "prebuilt_etc {",
        f'    name: "{etc_file.module_name}",',
        f'    src: "{etc_file.path.relative_to(APPS_DIR).as_posix()}",',
        f'    sub_dir: "{etc_file.sub_dir}",',
        f'    filename: "{etc_file.filename}",',
        *partition_lines(app),
        "}",
    ]
    return "\n".join(lines)


def render_module(app, info, etc_files):
    lines = [
        "android_app_import {",
        f'    name: "{app.name}",',
        f'    apk: "{app.apk_path.relative_to(APPS_DIR).as_posix()}",',
        "    preprocessed: true,",
    ]
    if app.privileged:
        lines.append("    privileged: true,")
    lines.extend(partition_lines(app))
    if app.overrides:
        lines.append(f"    overrides: [{render_string_list(app.overrides)}],")
    if info.required_libraries:
        lines.append(f"    uses_libs: [{render_string_list(info.required_libraries)}],")
    if info.optional_libraries:
        lines.append(f"    optional_uses_libs: [{render_string_list(info.optional_libraries)}],")
    if etc_files:
        required = render_string_list(file.module_name for file in etc_files)
        lines.append(f"    required: [{required}],")
    lines.append("}")
    return "\n".join(lines)


def render_blueprint(apps, aapt2):
    header = "/* Generated by scripts/fetch-apps.py from manifest.yml. Do not edit. */"
    privileged_permissions = load_privileged_permissions()
    modules, etc_files = [], []
    for app in apps:
        info = read_apk_info(app.apk_path, aapt2)
        app_etc_files = build_etc_files(app, info, privileged_permissions)
        modules.append(render_module(app, info, app_etc_files))
        modules.extend(render_etc_module(app, file) for file in app_etc_files)
        etc_files.extend(app_etc_files)
    return "\n\n".join([header, *modules]) + "\n", etc_files


def render_makefile(apps):
    names = " ".join(app.name for app in apps)
    return f"""# Generated by scripts/fetch-apps.py from manifest.yml. Do not edit.

_fetched_apps := {names}
_apps_dir := $(dir $(lastword $(MAKEFILE_LIST)))

$(foreach app,$(_fetched_apps),\\
    $(if $(wildcard $(_apps_dir)prebuilt/$(app).apk),,\\
        $(error $(app).apk is missing, run vendor/itsaky/scripts/fetch-apps.py)))

PRODUCT_PACKAGES += $(_fetched_apps)
"""


def write_if_changed(path, content):
    if path.exists() and path.read_text() == content:
        print(f"{path.name}: unchanged")
        return
    path.write_text(content)
    print(f"{path.name}: updated")


def write_etc_files(etc_files):
    ETC_DIR.mkdir(parents=True, exist_ok=True)
    for etc_file in etc_files:
        write_if_changed(etc_file.path, etc_file.content)
    wanted = {etc_file.path for etc_file in etc_files}
    for stale in ETC_DIR.glob("*.xml"):
        if stale not in wanted:
            print(f"removing stale {stale.name}")
            stale.unlink()


def main():
    apps = load_apps(MANIFEST)
    apksigner = find_apksigner()
    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
    for app in apps:
        fetch_app(app, apksigner)
    prune_unlisted_apks(apps)
    blueprint, etc_files = render_blueprint(apps, find_aapt2())
    write_etc_files(etc_files)
    write_if_changed(BLUEPRINT, blueprint)
    write_if_changed(MAKEFILE, render_makefile(apps))


if __name__ == "__main__":
    try:
        main()
    except (ManifestError, VerificationError, OSError) as error:
        sys.exit(f"error: {error}")
