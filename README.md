# RouteRevive

**Version 0.0.1 — Android MVP (local-only)**

RouteRevive helps local pressure-washing and service businesses recontact previous customers and track neighborhood repeat-service offers, without automated bulk messaging.

## Download the Android APK

1. Open [the successful v0.0.1 Android build](https://github.com/demetrecerrone-star/RouteRevive/actions/runs/37723262063).
2. Under **Artifacts**, choose **RouteRevive-v0.0.1-debug** (sign into GitHub if prompted).
3. Download and unzip the artifact, then install `app-debug.apk` on Android 8.0 or newer.
4. This is a development APK, **not a Google Play production release**. Android may ask you to allow installation from your files app. Install only if you trust your own repository's build.

GitHub Actions automatically runs unit tests and builds an APK whenever the main branch changes. Debug APKs built on separate runners may use different signing keys and require uninstalling the prior test version; uninstalling deletes locally stored records. Stable release signing and export/backups must be added before using live business data.

## Features in v0.0.1

- Add customer contact details, ZIP, prior service/date, and service amount.
- Enter a statement documenting customer marketing permission; suppress contacts lacking recorded permission.
- Suppress opted-out contacts, future-booked customers, customers with open issues, recent service (180 days), recent contact (60 days), duplicate phone records, and people already targeted in another active campaign.
- Build neighborhood offers with a specific service, appointment date, price, expiration, discount, and booking limit.
- Generate a per-customer message, edit and explicitly approve it.
- Open the owner's SMS app with prefilled recipient/message; **nothing is sent automatically**.
- Owner must separately confirm they actually sent the message. Opening the SMS app does not mean delivery.
- Mark interest, book confirmed appointments, complete service, and record actual paid revenue.
- Data persists locally using app-private Android SharedPreferences, with cloud backups disabled.

## Critical limitations

- **No authentication, cloud synchronization, team accounts, CSV import, billing, or payment processing.**
- No automatic SMS and no read/send SMS permissions.
- Permission evidence is recorded by the business owner but not independently verified by the app.
- Recipients can reply in the owner's SMS app; replies are not automatically captured by RouteRevive.
- ZIP-code grouping is used; there is not yet a live map or geographic route optimization.
- No secure export or recovery: uninstalling or clearing app data permanently loses the local customer and campaign records. **Use fictitious data for initial testing.**
- This is a functional **prototype**, not a ready-to-sell mass-marketing solution. Review TCPA and relevant state/federal privacy and text marketing requirements before any production use.

## Development

- Android Studio with JDK 17, Android SDK 35, Gradle 8.10.2 and Android Gradle Plugin 8.7.3.
- Kotlin / Jetpack Compose / Material 3.
- `gradle testDebugUnitTest assembleDebug`
- Package: `com.routerevive.app`, min SDK: 26.
- GitHub Actions workflow: `.github/workflows/android.yml`.

## Roadmap

- v0.0.2: customer editing, stronger consent evidence capture, CSV import, secure export, calendar booking windows.
- v0.0.3: business accounts, multi-device secure sync, stable signing and recoverable data.
- Later: service-area maps, self-service booking pages, compliant opt-in SMS provider, subscription billing.

**Do not contact real people until you have a legally suitable consent and privacy process in place.**
