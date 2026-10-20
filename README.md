# RouteRevive

A native Android workspace for small service businesses. Reconnect with previous customers by neighborhood, review every message, and keep bookings, service history, jobs and received payments together.

## The original workflow

1. Add past customers with their service history and contact preferences, or preview a customer CSV import.
2. Open **Overview → Ready to reconnect** to find eligible customers grouped by ZIP and service.
3. Build a neighborhood offer. Review the audience, exclusions, price, available slots and example message.
4. Approve each customer draft. Open your SMS app, review/send it yourself, then explicitly record that it was sent.
5. Record the reply and confirm a conflict-free appointment when the customer agrees.
6. Complete the actual service from Campaigns, Schedule or Jobs. The linked appointment, job and last-service date stay aligned.
7. Record actual payments. Campaigns and the main financial report use the same job receipt records, including partial payments.

Eligibility keeps the existing 180-day service interval and 60-day promotional-contact cooldown, documented permission, opt-outs, duplicate-phone checks, issue flags, active campaigns and upcoming bookings.

## Development status

**0.2.6 workflow upgrade** includes the shared green design system, audience previews, repeat-service suggestions, grouped tools, responsive forms and consistent navigation. Whole record sets are persisted before the UI adopts them; failed saves retain the prior screen data. Campaign actions now keep their linked booking, job, service history and receipts consistent. The encrypted version-7 record/backup format is unchanged.

Customer messages are reviewed and sent in the owner's SMS/email app. The shareable HTML inquiry form opens a draft; it does not submit requests to RouteRevive. Cloud snapshots support explicit restore/import, not simultaneous team editing or live merging. Payments are recorded locally; the app does not process charges.

## Run locally

Use **JDK 17**, Android SDK **36**, and Android SDK Build Tools **36.0.0**. Open this folder in Android Studio, or set `ANDROID_HOME` to your SDK:

```sh
./gradlew testDebugUnitTest lintRelease assembleDebug assembleDebugAndroidTest
```

Windows: use `gradlew.bat`. The checksum-pinned wrapper uses Gradle **8.11.1**; first builds need internet access. The preview APK is `app/build/outputs/apk/debug/app-debug.apk`. A debug APK cannot update a release-signed installation.

With an Android device/emulator connected, run `./gradlew connectedDebugAndroidTest`. The smoke suite uses fictional records in a dedicated test installation and exercises offer creation/approval/booking, service completion/payment reporting, keyboard navigation, large text and rotation. It does not launch the SMS app. GitHub Actions runs it on Android 16 and uploads reports/screenshots.

## Checks and releases

Pull requests run unit tests, release lint, debug builds and the Android screen tests without signing credentials. Successful main-branch builds additionally use the existing persistent signing secrets to build a release APK/AAB and keep the stable direct APK download URL.

Keep the existing application ID and signing certificate for in-place updates. Export an encrypted backup before upgrading; uninstalling removes the local encryption key. Never commit signing files, credentials, real customer data or backup archives.

## Developer guide

- [Code map, verification, compatibility and next work](docs/DEVELOPMENT.md)
- [Earlier release notes and upgrade details](docs/RELEASE_HISTORY.md)
- [Private cloud setup](docs/SUPABASE_SETUP.md)
- [Beta release checklist](docs/BETA_RELEASE_CHECKLIST.md)
- [Privacy policy draft](docs/PRIVACY_POLICY_DRAFT.md)
- [Play Store listing draft](docs/PLAY_STORE_BETA_DRAFT.md)
