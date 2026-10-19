# android_vendor_itsaky

Personal additions to AOSP.

## Prebuilt apps

Apps are listed in `apps/manifest.yml`. Before building, run:

    vendor/itsaky/scripts/fetch-apps.py

It downloads each APK into `apps/prebuilt/` (git-ignored), verifies its checksum
and signing certificates, and regenerates `apps/Android.bp` and `apps/apps.mk`.
