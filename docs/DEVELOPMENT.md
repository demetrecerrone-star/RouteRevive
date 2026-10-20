# RouteRevive development

## Current foundation

This is a Kotlin + Jetpack Compose Android app. Customer, campaign, appointment, job, business and booking-request records are saved through `LocalStore`. The existing encrypted record schema and backup format are unchanged by the 0.2.5 design refresh. No hosted booking endpoint, payments provider or shared-workspace server is introduced.

## Code map

Paths below are relative to `app/src/main/java/com/routerevive/app/`.

| Area | Files |
| --- | --- |
| Entry, app state, persistence callbacks | `MainActivity.kt` |
| Design system and shared UI | `Theme.kt`, `AppComponents.kt` |
| Header, bottom navigation, screen ownership and back behavior | `AppShell.kt`, `AppNavigation.kt` |
| Overview, customers, grouped tools | `Dashboard.kt`, `CustomersScreen.kt`, `MoreScreen.kt` |
| Appointments and customer/campaign forms | `ExtraScreens.kt`, `MainActivity.kt` |
| Jobs, photos and invoices | `JobsScreen.kt`, `JobData.kt`, `JobFiles.kt` |
| Local models and encrypted storage | `Data.kt` |
| Reports and CSV import | `BusinessLogic.kt`, `BusinessScreen.kt`, `BusinessInsights.kt` |
| Customer history and repeat bookings | `CustomerHistory.kt`, `RecurringBooking.kt` |
| Requests and HTML inquiry form | `BookingRequests.kt`, `RequestsScreen.kt`, `CustomerBookingPage.kt` |
| Cloud snapshots and scheduled backups | `CloudApi.kt`, `CloudManagement.kt`, `CloudScreen.kt`, `AutomaticCloud.kt` |
| Reminders and release checks | `BusinessAlerts.kt`, `BetaIntegrity.kt`, `BetaReadinessScreen.kt` |

Tests live under `app/src/test/java/com/routerevive/app/`. Business rules are separate from Compose UI where possible.

## Verify a change

```sh
./gradlew testDebugUnitTest lintRelease assembleDebug
```

Use a test device or emulator to review Overview, Customers, Schedule, Jobs, and More, then nested customer/campaign screens. Check small widths, Android system bars, keyboard visibility and large system fonts. Exercise business rules with fictional records. Keep consent defaults, opt-out blocking, manual message approval, appointment conflict checks and encrypted backup behavior intact.

For release candidates, follow the separate beta checklist, including an in-place upgrade over a release-signed installation and encrypted backup/restore with photos. A passing debug build does not establish signing compatibility or validate a configured cloud account.

## Release path

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts` for each installable release.
2. Open a pull request. CI runs tests, lint and `assembleDebug`, then uploads a preview APK and check reports.
3. Main-branch CI requires `RR_KEYSTORE_BASE64` and `RR_STORE_PASSWORD`. The keystore alias remains `routerevive`; do not rotate the existing signing identity.
4. CI builds a signed APK/AAB, removes the runner's keystore, and publishes the APK with the stable `RouteRevive.apk` filename.

Wrapper downloads are pinned to Gradle 8.11.1 and its distribution checksum. Keep local SDK paths in ignored `local.properties` or `ANDROID_HOME`, never in committed build files.

## Next development work

These are open follow-up tasks, not implemented features.

1. Carry the shared components into Schedule, Jobs, Business, Requests and Cloud. Check dense forms at large font sizes; keep the existing resizable map behavior.
2. Extract customer and campaign forms from the remaining main activity. Introduce a lifecycle-aware state holder and explicit navigation routes in a separate behavior-preserving change.
3. Add focused Compose interaction coverage for booking confirmation, customer editing and error/empty states.
4. Improve save-failure handling: persistence callbacks currently update in-memory lists before disk save completes. Design rollback or staged commits before changing data writes.
5. Run release-signed in-place upgrade and full encrypted backup/restore tests using a dedicated fictional dataset.
6. Prioritize the next business workflow before adding hosted booking, payment collection or multi-user cloud features.

## Refresh scope

- Shared palette, card styles, icon treatments, spacing and narrow/large-font fallback for dashboard pairs.
- Overview with actionable metrics, upcoming appointments, business tools, payments and expandable local backup controls.
- Grouped More tools, discoverable requests badge, customer search empty state and wrapping card actions.
- The shell reserves keyboard space and clears text-field focus when switching screens.
- Customer detail pages retain Customers selection; nested back navigation follows the owning section. Android Back returns other primary tabs to Overview.
- Screen and selected-record state survive activity recreation; unsaved form drafts are not preserved by this pass.
- Removed the duplicate unused home dashboard and its obsolete version text. Current version text comes from `BuildConfig`.
- README focuses on current setup; earlier notes remain in `RELEASE_HISTORY.md`.
