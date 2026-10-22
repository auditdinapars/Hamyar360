# همیار ۳۶۰ — نسخهٔ آزمایشی ۰.۵

Android app in Persian with separate task, receivable and payable menus.

## Features

- Tasks, receivables and payables with editing, deletion, completion and reopening.
- Persian (Jalali) month calendar, date picker and per-day due records.
- Reports by type, completed/open/overdue status and due-date range; CSV export.
- Voice buttons in every menu and in the editor launch the phone's Persian speech recognizer. Recognized text becomes an editable draft with pattern-based title, amount and date extraction. Confirmation is required; no audio recording is stored. Recognition availability and internet requirements depend on the installed speech provider.
- Camera buttons launch the phone camera. The confirmed photo is compressed and attached to the record. This version does not perform OCR or infer fields from images.
- IndexedDB storage for records and photos, with automatic migration from the original localStorage database. JSON backup/restore includes photos. Original data is left in place during migration.
- Completion timestamps for newly completed items. Older completed items retain their status but have no historical completion timestamp.

## Build and APK

Actions > Build Android APK runs core tests, Android compilation and lint. After a successful run, download the `Hamyar360-debug-apk` artifact and extract `app-debug.apk`. See [BUILD-STATUS.md](BUILD-STATUS.md) for requirements and verification limits.

## Upgrade and data

Back up your data before reinstalling. This is a debug build; fresh CI runners may use a different signing key, preventing in-place updates. Never uninstall an existing app before exporting its backup. Clearing app data removes records and attachments. JSON backups and local app data are not encrypted.

Automated reminders, cloud sync and OCR are not included. Test camera, Persian recognition, backups and installation on the target phone before relying on the prototype.
