# RouteRevive development

## Current foundation

Kotlin + Jetpack Compose, Android API 26–36, JDK 17 and Gradle 8.11.1. This is an owner-operated repeat-service and job organizer, with encrypted local records and optional private cloud snapshots. The 0.2.6 upgrade retains the existing version-7 record/backup format and signing identity.

## Code map

Paths are relative to `app/src/main/java/com/routerevive/app/`.

| Area | Files |
| --- | --- |
| Activity, screen routing, restore callbacks | `MainActivity.kt` |
| Shared theme, cards, responsive pairs, date/time inputs | `Theme.kt`, `AppComponents.kt` |
| Header, navigation and screen ownership | `AppShell.kt`, `AppNavigation.kt` |
| Persist-first snapshots and linked booking/job operations | `Workspace.kt` |
| Indexed eligibility, offer lifecycle and campaign finances | `CampaignWorkflow.kt` |
| Campaign list, builder and customer approval queue | `CampaignScreens.kt` |
| Overview, customers and grouped tools | `Dashboard.kt`, `CustomersScreen.kt`, `MoreScreen.kt` |
| Schedule and customer editor | `ExtraScreens.kt` |
| Jobs, photos and invoice exports | `JobsScreen.kt`, `JobData.kt`, `JobFiles.kt` |
| Local models and encrypted record storage | `Data.kt` |
| Reports, profile and CSV import | `BusinessLogic.kt`, `BusinessScreen.kt`, `BusinessInsights.kt` |
| History and repeat bookings | `CustomerHistory.kt`, `RecurringBooking.kt` |
| Inbound requests and HTML form | `BookingRequests.kt`, `RequestsScreen.kt`, `CustomerBookingPage.kt` |
| Cloud snapshots and scheduled backups | `CloudApi.kt`, `CloudManagement.kt`, `CloudScreen.kt`, `AutomaticCloud.kt` |
| Reminders and release checks | `BusinessAlerts.kt`, `BetaIntegrity.kt`, `BetaReadinessScreen.kt` |

## Record changes

`WorkspaceController.commit` transforms an immutable snapshot, persists the complete proposed record set, and only then publishes it to Compose. Validation or write errors leave the previous controller/UI state in place. Operations that affect multiple records use one commit: message-sent/contact-date, confirmed inbound request/customer/appointment, campaign booking/appointment, completion/job/service history and receipt/campaign total.

Load imported/restored records through `reloadRecords` after the storage operation succeeds. All six record groups must reload, including booking requests. Do not mutate record lists or perform a sequence of partial saves in UI callbacks.

Completion from Schedule, Campaigns or Jobs updates the same appointment, job and last-service date. It cannot be recorded before the planned service date; move the appointment to its actual date when needed. Completed progress cannot be silently reopened from Jobs. Manual contact-preference and external-booking flags remain owner-controlled.

Job receipts are the dated financial source. Campaign payment entry appends to that ledger and supports partial payments without marking the service Paid prematurely. Older campaign-only payment totals remain visible, without inventing missing receipt dates or silently erasing them. They require explicit review in Jobs before recording more money through the campaign.

Eligibility uses the existing 180-day service interval and 60-day contact cooldown. An indexed context shares the same rules across Overview, the builder, and send-time checks. Approval is invalidated when relevant customer/eligibility fields change. Sending remains a manual external SMS action; recording a sent message does not send anything.

## Verification

```sh
./gradlew testDebugUnitTest lintRelease assembleDebug assembleDebugAndroidTest
```

With a test device/emulator connected:

```sh
./gradlew connectedDebugAndroidTest
```

The Android smoke suite seeds fictional records in its test installation. It covers building/reviewing/booking an offer, completion and one-ledger payment reporting, nested job back navigation, focused search → More, large system text and rotation. Screenshots are written to the test app's `smoke-screens` directory. `scripts/android-smoke.sh` runs the suite and collects screenshots/logs for CI.

Unit regression cases cover failed persistence/retry, cross-record consistency, capacity and scheduling conflicts, non-finite/fractional amounts, partial/full receipts, opt-outs, repeat eligibility, legacy campaign money and request confirmation.

## Release path

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts` for each installable release.
2. Pull-request CI runs unit tests, release lint and preview builds, then the screen suite on Android 16. Reports, preview APKs and emulator evidence are attached to the run.
3. Main-branch signed builds wait for both check jobs. `RR_KEYSTORE_BASE64` and `RR_STORE_PASSWORD` remain required; the alias is `routerevive`.
4. CI builds the signed APK/AAB, removes the runner's keystore and publishes `RouteRevive.apk` under the stable release download name.

Release-signed in-place upgrades and a full encrypted backup/restore with photos still belong in the beta release checklist. Test a configured optional cloud account separately. A debug/emulator test does not establish release-certificate compatibility or Play Console approval.

## Next work

- Extend the device matrix below Android 16 and test a release-signed upgrade on the owner's phone.
- Move remaining activity routing into a lifecycle-aware state holder, then consider background persistence for very large workspaces.
- Continue shared-layout adoption in the dense Cloud and financial-report views.
- Prioritize any hosted booking/payment/team features separately; they are not part of the current local workflow.
