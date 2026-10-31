# Aura Camera — Redmi Note 10 Pro Max

An offline-first, OPPO-inspired camera app prototype for the Redmi Note 10 Pro Max.

## Current prototype scope
- Native Android Camera2 access.
- Rear camera enumeration and largest advertised JPEG output selection.
- Basic live preview and JPEG still capture.
- No internet permission or cloud services.

## Important
This is an **early engineering scaffold**, not a finished camera app and not yet verified on the target phone. A reported 108 MP sensor does not guarantee that MIUI exposes a 108 MP JPEG stream to third-party apps. The app must be installed on the target Redmi and its advertised output dimensions checked.

Portrait segmentation, multi-frame night mode, HDR fusion, OPPO-style colour rendering, gallery integration, camera switching, zoom, and robust lifecycle handling remain future implementation/testing work.

## Build
Open this folder in Android Studio (recent stable version), allow Gradle sync, then Build > Build APK(s).

## Device validation
1. Install debug APK on Redmi Note 10 Pro Max.
2. Grant camera permission.
3. Note the largest JPEG dimensions shown under the preview.
4. Capture daylight, indoor, backlit portrait and night samples.
5. Compare with matching stock Redmi and OPPO F21 Pro 5G reference shots.

## User device target
- Redmi Note 10 Pro Max, user-reported Android 13 / MIUI 14.
- 108 MP Samsung HM2 main camera, per Xiaomi published specifications.
- Visual reference: OPPO F21 Pro 5G (CPH2341), ColorOS 12.

The visual style is independently implemented; this project does not contain OPPO proprietary camera algorithms or assets.
