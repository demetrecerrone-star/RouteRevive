# RouteRevive

A native Android workspace for small service businesses. Manage customers, appointments, jobs, invoices and repeat-service campaigns, with encrypted local records and optional private cloud backups.

## Development status

**0.2.5 development refresh** builds on the existing 0.2.4 feature set. This pass introduces a shared green design system, a clearer Overview, grouped business tools, responsive customer cards and consistent tab/back navigation. It also removes an unused dashboard and separates the app shell, navigation and theme from the main activity.

## Run locally

Use **JDK 17**, Android SDK **36**, and Android SDK Build Tools **36.0.0**. Open this folder in Android Studio, or set `ANDROID_HOME` to your SDK and run:

```sh
./gradlew testDebugUnitTest lintRelease assembleDebug
```

Windows: use `gradlew.bat`. The checked-in wrapper pins Gradle **8.11.1**; a separate Gradle installation is not required. First builds need internet access for dependencies. Install `app/build/outputs/apk/debug/app-debug.apk` on a test device. A debug build cannot update a release-signed installation.

## What works

- Customer records, service history, notes, consent and opt-out controls, CSV import.
- Appointment scheduling, conflict checks, day/week views, repeat bookings and manual reminder drafts.
- Job progress, photos, invoice line items, received payments and local business analytics.
- Owner-managed booking request inbox and a shareable HTML inquiry form.
- Neighborhood maps, route suggestions and repeat-service campaigns with individual message approval.
- Password-encrypted full backup/restore, optional Supabase accounts and private encrypted cloud snapshots.
- Optional device credential gate and private local reminders.

Customer messages are reviewed and sent in the owner's SMS/email app. The HTML inquiry form opens a draft; it does not submit requests to RouteRevive. Cloud snapshots support explicit restore/import, not simultaneous team editing or live record merging. Payments are recorded locally; this app does not process them.

## Checks and release builds

Pull requests run unit tests, release lint and a debug APK build without signing credentials. Preview APKs and check reports are attached to the workflow run. Main-branch builds additionally use the existing persistent signing secrets to produce the release APK and AAB. Successful main-branch releases keep the existing direct APK download URL.

Keep the existing application ID and signing certificate when updating an installed release. Export an encrypted backup before installing updates; uninstalling removes the local encryption key. Never commit signing files, credentials, real customer data or backup archives.

## Developer guide

- [Code map, verification and next development work](docs/DEVELOPMENT.md)
- [Earlier release notes and upgrade details](docs/RELEASE_HISTORY.md)
- [Private cloud setup](docs/SUPABASE_SETUP.md)
- [Beta release checklist](docs/BETA_RELEASE_CHECKLIST.md)
- [Privacy policy draft](docs/PRIVACY_POLICY_DRAFT.md)
- [Play Store listing draft](docs/PLAY_STORE_BETA_DRAFT.md)
