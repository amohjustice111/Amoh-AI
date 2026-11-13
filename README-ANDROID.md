# AMOH AI Android Launcher

This is the native Android launcher module for AMOH AI.

## Features
- Appears as an Android Home app so the user can select it as the default launcher.
- Lists apps with launcher icons.
- Keeps selected apps out of the AMOH AI app list.
- PIN-protected Private Apps screen.
- Opens hidden apps from the private screen and supports restoring them.

## Important Android limitation
This hides apps only from the AMOH AI launcher interface. Android Settings, Play Store, notifications, and other launchers may still show installed apps. This is not device-wide app hiding.

## Build
Run `gradle assembleDebug` with JDK 17 and Android SDK installed. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions builds the debug APK on pushes to the Android project and uploads it as an Actions artifact.
