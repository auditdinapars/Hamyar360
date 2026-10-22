# Build status

The project uses Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17 and Android SDK 35. A Gradle wrapper is not included; install Gradle 8.9 for local builds.

Run `gradle --no-daemon --stacktrace :app:assembleDebug :app:lintDebug` with ANDROID_HOME set to your Android SDK. Output: `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow installs the SDK and Gradle, builds and checks the app, and uploads the APK and SHA-256 checksum as Hamyar360-debug-apk. Reports and artifacts are retained for 14 days. No custom secrets are required.

Local checks passed: source ZIP integrity, manifest XML parsing, JavaScript syntax, required source files and packaging. Android compilation is verified only by a successful Actions run for the corresponding commit. Device behavior has not been tested.

This is a debug prototype. Fresh CI runners can generate different debug signing keys; back up data before uninstalling a previous build. App data and JSON backups are unencrypted.
