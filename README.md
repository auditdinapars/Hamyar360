# همیار ۳۶۰ — Android prototype 0.4

Offline Persian RTL Android WebView app: tasks, payment/receipt obligations, pattern-based Persian message parsing, and JSON backup/restore. Data is stored locally in WebView storage.

## Build and download

Open Actions > Build Android APK. Builds run on pushes and pull requests to main, or through Run workflow. After a successful run download the Hamyar360-debug-apk artifact, extract it, and install app-debug.apk. The artifact also contains its SHA-256 checksum.

See [BUILD-STATUS.md](BUILD-STATUS.md) for local build requirements and verification limits.

## Prototype limitations

No cloud service, OCR, AI integration, automated notifications, bank integration or legal monitoring is implemented. Local storage and JSON backups are unencrypted. Clearing app data or uninstalling can remove records. This is a debug build, not a production release, and it has not been device-tested.
