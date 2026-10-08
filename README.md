# RouteRevive

**Version 0.0.3 — Android MVP with neighborhood mapping (local-only)**

## New in v0.0.3: Neighborhoods

- Open the **Map** tab to view an interactive OpenStreetMap map and stored customer pins.
- First add a street address to each customer. Tap **Locate** next to an unmapped address to search for its position; review the proposed address and confirm before saving the pin.
- If your Android device cannot geocode addresses, open **Customers → Edit profile** and enter known latitude/longitude manually.
- Select an **anchor customer** and a radius from 1 to 25 miles to see other eligible repeat-service customers ranked by straight-line proximity. Matches use the same ZIP and service as the anchor, plus existing consent, opt-out, and booking rules.
- Tap **Create matching neighborhood campaign** to prefill ZIP and service in the existing campaign builder.
- Choose a service date to view your scheduled, mapped appointments in appointment-time order, then explicitly approve opening their locations in Google Maps for driving directions.
- Distances shown inside RouteRevive are straight-line comparisons only. **Road travel distances, route feasibility and ETA are NOT calculated in RouteRevive.** Confirm driving directions and time windows in the navigation app.

**Privacy:** Street-address lookup may send the selected address to Android's geocoding provider. OpenStreetMap tile services receive the geographical area being viewed. Tapping driving directions opens Google Maps with the chosen stops' coordinates. No precise device location permission is requested. Customer pins are kept in the app-private local database and included in JSON exports. Protect exported customer data.


## APK signing setup (required for future in-place updates)

This repository now expects a private persistent signing key for published APKs. The key must **never** be committed to this public repository. A one-time setup is required:

1. Generate or retain the owner's private PKCS12 signing key and password in a secure offline backup.
2. Base64-encode the PKCS12 keystore and add a repository Actions secret named `RR_KEYSTORE_BASE64`.
3. Add the matching password under `RR_STORE_PASSWORD`.
4. The GitHub workflow runs tests and creates an APK signed with this key. Use the **RouteRevive-v0.0.3-signed** artifact from a successful run.
5. For every update, reuse the **same secrets**, increment `versionCode`, and keep the Android `applicationId` unchanged.

**The previous v0.0.1 and v0.0.3 debug APKs used ephemeral signing keys.** These APKs are not directly upgradable to this new permanent signature. If no important data exists, uninstall the debug APK before installing the newly signed v0.0.3 release once. That creates the stable update path going forward.



RouteRevive helps local pressure-washing and service businesses recontact previous customers and track neighborhood repeat-service offers, without automated bulk messaging.

## Download the Android APK

1. Once signing secrets are configured, open [the Android build history](https://github.com/demetrecerrone-star/RouteRevive/actions/workflows/android.yml).
2. Under **Artifacts**, choose **RouteRevive-v0.0.3-signed** (sign into GitHub if prompted).
3. Download and unzip the artifact, then install `app-debug.apk` on Android 8.0 or newer.
4. This is a privately signed testing release, **not yet a Google Play production release**. Android may ask you to allow installation from your files app. Install only if you trust your own repository's build.

GitHub Actions automatically runs unit tests and builds an APK whenever the main branch changes. Debug APKs built on separate runners may use different signing keys and require uninstalling the prior test version; uninstalling deletes locally stored records. Stable release signing and export/backups must be added before using live business data.

## Features in v0.0.3

- Add and **edit** customer contact details, ZIP, prior service/date, street address, notes and service amount.
- Search customers by name, phone, ZIP, or service.
- Enter a statement documenting customer marketing permission; suppress contacts lacking recorded permission.
- Suppress opted-out contacts, future-booked customers, customers with open issues, recent service (180 days), recent contact (60 days), duplicate phone records, and people already targeted in another active campaign.
- Build neighborhood offers with a specific service, appointment date, price, expiration, discount, and booking limit.
- Generate a per-customer message, edit and explicitly approve it.
- Open the owner's SMS app with prefilled recipient/message; **nothing is sent automatically**.
- Owner must separately confirm they actually sent the message. Opening the SMS app does not mean delivery.
- Mark interest, book confirmed appointments, complete service, and record actual paid revenue.
- Create local appointments with date/time, duration, notes and price; prevent overlapping bookings.
- Campaign bookings appear on the appointment schedule.
- Export a JSON backup using Android's document picker; import with confirmation and format validation.
- Data persists locally using app-private Android SharedPreferences, with cloud backups disabled.

## Critical limitations

- **No authentication, cloud synchronization, team accounts, CSV import, billing, or payment processing.**
- No automatic SMS and no read/send SMS permissions.
- Permission evidence is recorded by the business owner but not independently verified by the app.
- Recipients can reply in the owner's SMS app; replies are not automatically captured by RouteRevive.
- ZIP-code grouping is used; there is not yet a live map or geographic route optimization.
- Export/import is available as unencrypted JSON: the backup contains personal information. Store files in a private, trusted location and protect access. Restore replaces local data.
- Uninstalling still deletes in-app records; export a backup **before** uninstalling.
- Because v0.0.1's APK came from an ephemeral GitHub Actions runner debug key, v0.0.3 might not install over v0.0.1. If Android reports an incompatible signature, **uninstalling v0.0.1 deletes its data, and v0.0.1 has no export feature**. Do not uninstall if you need those records; preserve them or contact the developer about data extraction before changing versions.
- Future APKs need one consistent private signing key (for instance, configured via GitHub Actions encrypted secrets) to support non-destructive updates. Do not commit a private signing key to the public repository.
- **Use fictitious data for initial testing.**
- This is a functional **prototype**, not a ready-to-sell mass-marketing solution. Review TCPA and relevant state/federal privacy and text marketing requirements before any production use.

## Development

- Android Studio with JDK 17, Android SDK 35, Gradle 8.10.2 and Android Gradle Plugin 8.7.3.
- Kotlin / Jetpack Compose / Material 3.
- `gradle testDebugUnitTest assembleDebug`
- Package: `com.routerevive.app`, min SDK: 26.
- GitHub Actions workflow: `.github/workflows/android.yml`.

## Roadmap

- v0.0.3: basic customer editing and search, scheduling, JSON export/import (this version).
- v0.0.4: CSV import, encrypted storage, more flexible appointment time windows, advanced optional driving-route integration.
- v0.0.3: business accounts, multi-device secure sync, stable signing and recoverable data.
- Later: service-area maps, self-service booking pages, compliant opt-in SMS provider, subscription billing.

**Do not contact real people until you have a legally suitable consent and privacy process in place.**
