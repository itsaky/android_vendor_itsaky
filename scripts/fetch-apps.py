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
  overrides      names of modules this app replaces (default none)

Libraries the APK declares with <uses-library> are read from its manifest and
passed to Soong, which otherwise fails its uses-library check.

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

PARTITION_PROPERTIES = {
    "system": None,
    "system_ext": "system_ext_specific",
    "product": "product_specific",
    "vendor": "soc_specific",
}
MODULE_NAME = re.compile(r"^[A-Za-z0-9_.-]+$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
USES_LIBRARY = re.compile(r"^uses-library(-not-required)?:'([^']+)'$", re.M)
SIGNER_DIGEST = re.compile(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})$", re.M)
DOWNLOAD_TIMEOUT_SECONDS = 60
CHUNK_BYTES = 1 << 20


class ManifestError(Exception):
    pass


class VerificationError(Exception):
    pass


@dataclass(frozen=True)
class UsesLibraries:
    required: tuple
    optional: tuple


@dataclass(frozen=True)
class App:
    name: str
    url: str
    sha256: str
    signing_certs: frozenset
    partition: str
    privileged: bool
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


def read_uses_libraries(apk, aapt2):
    result = subprocess.run(
        [aapt2, "dump", "badging", str(apk)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise VerificationError(f"{apk.name}: aapt2 failed: {result.stderr.strip()}")
    required, optional = [], []
    for not_required, library in USES_LIBRARY.findall(result.stdout):
        (optional if not_required else required).append(library)
    return UsesLibraries(tuple(required), tuple(optional))


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


def render_string_list(libraries):
    return ", ".join(f'"{library}"' for library in libraries)


def render_module(app, libraries):
    lines = [
        "android_app_import {",
        f'    name: "{app.name}",',
        f'    apk: "{app.apk_path.relative_to(APPS_DIR).as_posix()}",',
        "    preprocessed: true,",
    ]
    if app.privileged:
        lines.append("    privileged: true,")
    partition_property = PARTITION_PROPERTIES[app.partition]
    if partition_property:
        lines.append(f"    {partition_property}: true,")
    if app.overrides:
        lines.append(f"    overrides: [{render_string_list(app.overrides)}],")
    if libraries.required:
        lines.append(f"    uses_libs: [{render_string_list(libraries.required)}],")
    if libraries.optional:
        lines.append(f"    optional_uses_libs: [{render_string_list(libraries.optional)}],")
    lines.append("}")
    return "\n".join(lines)


def render_blueprint(apps, aapt2):
    header = "/* Generated by scripts/fetch-apps.py from manifest.yml. Do not edit. */"
    modules = [
        render_module(app, read_uses_libraries(app.apk_path, aapt2)) for app in apps
    ]
    return "\n\n".join([header, *modules]) + "\n"


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


def main():
    apps = load_apps(MANIFEST)
    apksigner = find_apksigner()
    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
    for app in apps:
        fetch_app(app, apksigner)
    prune_unlisted_apks(apps)
    write_if_changed(BLUEPRINT, render_blueprint(apps, find_aapt2()))
    write_if_changed(MAKEFILE, render_makefile(apps))


if __name__ == "__main__":
    try:
        main()
    except (ManifestError, VerificationError, OSError) as error:
        sys.exit(f"error: {error}")
