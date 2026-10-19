# Entry point for everything in vendor/itsaky

# Include CustomParts
PRODUCT_PACKAGES += CustomParts
PRODUCT_PACKAGE_OVERLAYS += vendor/itsaky/overlay

# Prebuilt apps from apps/manifest.yml
-include vendor/itsaky/apps/apps.mk
