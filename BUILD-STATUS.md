# Build and validation — v0.5

Requirements: JDK 17, Gradle 8.9, Android SDK platform 35 and Build Tools 34.0.0. Android Gradle Plugin: 8.7.3. AndroidX Core: 1.13.1 for FileProvider. Minimum Android API: 24. Set ANDROID_HOME to the SDK directory. A Gradle wrapper is not included.

```sh
node --test tests/core.test.cjs
gradle --no-daemon --stacktrace :app:assembleDebug :app:lintDebug
```

APK path: `app/build/outputs/apk/debug/app-debug.apk`. The workflow uploads APK and SHA-256 checksum as `Hamyar360-debug-apk` and lint reports separately, retained for 14 days. No custom secrets are needed.

Local automated checks cover Persian leap dates and boundaries, voice-draft parsing, reports, invalid backups and CSV escaping. Browser integration checks cover migration from v0.4, separate menus, mocked native speech/photo results, attachment persistence, completion, filters, CSV, calendar/picker, JSON round-trip and invalid import rejection. Native camera and speech hardware are not exercised by browser tests. Android build success is indicated by the Actions result for the corresponding commit; phone installation and hardware behavior require device testing.
