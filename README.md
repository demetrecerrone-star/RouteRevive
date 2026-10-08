# RouteRevive v0.2.3 — Customer history, recurring bookings, calendar and job progress

New in v0.2.3: **Customers → History** displays dated service records, appointments, manual reminder records, job status changes, photos and received job payments for that customer. It is a timeline derived from the saved record snapshot, **not** a complete audit log of every edit.

**Schedule → Day / Week** has date navigation and a 7-day calendar strip with daily appointment counts. The existing Upcoming and All views remain. **Repeat booking** creates 1–12 independent weekly, every-two-weeks, or monthly appointments. The entire group is checked for overlaps before any booking is saved; on conflict no part of the new series is created. A monthly series uses the original day-of-month anchor, so the dates will not drift after February. Generated bookings remain editable or cancellable individually and are not background-generated forever.

**Jobs → Job progress** can mark work Planned, In progress, On hold or Completed, with a saved last-change date. The appointment status and billing records stay independent. Job filters make it easier to find active, held, or finished work.

This release uses the existing version-7 encrypted record/backup format, with optional new job-status fields that default safely when reading earlier data. **Install signed v0.2.3 (versionCode 14) over the current release—do not uninstall or clear app data.** Create an encrypted backup first.

---

# RouteRevive v0.2.2 — Business analytics and private reminders

New in v0.2.2: **More → Business analytics** shows a 6/12-month chart of actual job payment receipts, total recorded collections, issued invoice balances, overdue invoices, customers with multiple recorded bookings, and completed appointments. This information is computed from the local records on your phone; it is not sent to an analytics service.

**More → Reminders & alerts** lets the owner opt in to a private daily summary (approximately 8 AM subject to Android background scheduling). Select upcoming appointments, overdue invoices, and/or automatic cloud backup problems. On Android 13+ the app asks for notification permission only when enabling reminders. Notifications show counts and a backup status, never customer names, addresses, or amounts. No customer SMS, email, or payment requests are sent automatically. Turning reminders off cancels the schedule.

**Install the signed v0.2.2 APK over your current app without uninstalling**. The version code has been increased to 13; existing encrypted local records and cloud settings stay on this installation. Keep an encrypted backup before upgrading. Tests and release lint run in GitHub Actions.

---

# RouteRevive v0.2.1 — Automatic encrypted cloud backups and safe snapshot sync

New in v0.2.0: the app can connect to a Supabase project, create/sign in to a business account using email/password, remember its session using Android Keystore-encrypted refresh credentials, upload new password-encrypted full backups (with job photos) to a private RLS-protected cloud bucket, list recent snapshots, and restore a chosen snapshot on another phone.

**Manual encrypted backup/restore remains supported; v0.2.1 also introduces opt-in daily or weekly background encrypted snapshots and explicit import of newer snapshots on a second phone.** Imports require confirmation and are blocked when both devices have changed since their common snapshot. This is NOT real-time live synchronization or merge-capable team editing. Manual restores still require a destructive replace confirmation. Offline local use continues unchanged. There is no live public booking endpoint, staff sharing, concurrent multi-device merging, or payment integration.

To connect it to a real cloud, the business must create its own Supabase project, run a secure private Storage bucket / user-UUID Row Level Security policy, and enter the **public** project URL + publishable/anon key in More → Private cloud backups. **Never enter a service_role or secret key.** Follow [Supabase setup instructions](docs/SUPABASE_SETUP.md) before using a cloud account. Until then, cloud features remain disconnected.

**Installation:** v0.2.0 is the same Android app ID and stable signing certificate, with versionCode 11 and target SDK 36. Install the signed APK directly over signed v0.1.0 without uninstalling. Export a separate encrypted local backup first. GitHub Actions also builds a signed AAB for later Google Play testing; this is not an approved public release.

**Privacy review:** The optional Supabase Auth service processes account emails and login metadata; Supabase Storage only receives the end-to-end encrypted backup archive. Update the public privacy policy/Data safety answers before uploading the next Play AAB. See [privacy draft](docs/PRIVACY_POLICY_DRAFT.md) and [beta release checklist](docs/BETA_RELEASE_CHECKLIST.md).

---

# RouteRevive v0.1.0 — Beta Readiness

RouteRevive is a **local-first Android service-business organizer**, not a certified production release.

**What's new in 0.1.0:** A private **More → Beta readiness & privacy** preflight checks records, appointments, financial amounts and missing photos; encrypted backup import validates before replacing any live records; photo restore prevents same-name collisions and rolls back newly copied files if import fails; the app tracks the most recent successful full backup date.

The Android build now targets **API 36** (required for new standard Android Google Play submissions after August 31, 2026), with AGP 8.10.1, Gradle 8.11.1, unit tests and release lint. GitHub Actions builds a **signed APK** for direct Android testing and a **signed AAB** for Play Console internal/closed testing. Both are still beta candidates and must pass real-device tests.

**Update:** Install the v0.1.0 signed APK directly over the previous v0.0.9 signed release. **Do not uninstall** or clear app data, because that destroys Android Keystore-encrypted local customer records. Export a full encrypted backup before updating.

**Beta/Google Play preparation:** Read [beta release checklist](docs/BETA_RELEASE_CHECKLIST.md), [privacy policy draft (not published)](docs/PRIVACY_POLICY_DRAFT.md), and [Play listing draft](docs/PLAY_STORE_BETA_DRAFT.md). A public HTTPS privacy policy, verified developer contact, Play Data safety answers, and account-specific testing still require owner action. A working APK is not Google Play approval.

**Limitations:** No live public booking URL, simultaneous edit/merge cloud sync, automated SMS, payment processing, or real-traffic route optimization. The app is not suitable for uncontrolled broad use with real customer data until security, privacy and compliance review has been completed.

---

# RouteRevive v0.0.9 — Booking Page & Navigation Polish

## New in this update
- **Five-tab navigation**: Overview, Customers, Schedule, Jobs, and More. Under More: Neighborhood Map, Requests, Campaigns, Business & Finances, and Booking Request Page. This keeps Schedule in one-tap reach and makes space for future business tools.
- **Customer-facing request page**: More → Booking request page can generate and share a branded `RouteRevive-Booking-Request.html` file after a business name and valid phone or email are saved. The mobile-friendly HTML form collects name, phone, ZIP, requested service, preferred date/time, and details. When opened in a compatible browser, pressing the button launches the customer's own SMS or email app with a prefilled inquiry addressed to your business. The customer must **send** it, and the business must manually enter/confirm a request in RouteRevive. It is NOT a hosted public website, live calendar, backend-connected booking portal, payment processor, or automatic data intake. To make it publicly accessible, publish the file on an HTTPS website you control; the app itself does not host it. Some apps may not open HTML attachments.
- **Appointment management improvements**: Today/Upcoming/All filters, counts, confirmation before completion or cancellation, prevention of rescheduling/creating appointments in the past, and existing conflict/midnight checks.
- **Future account foundation**: generate and persist a local workspace UUID in the encrypted business profile. Included in encrypted full backups. This is for future migration and does **not** create online accounts, authentication, shared team permissions or cloud synchronization.
- **Smaller screen polish**: top titles truncate appropriately; compact navigation avoids crowding the header.
- Existing local customer, photo, payment, invoice, map, campaign, request and reminder data remains compatible. Encrypted backup schema version 7, older archives still import.

## Safe update
Signed Android application ID: `com.routerevive.app`, versionCode 9, versionName 0.0.9. Install the `RouteRevive-v0.0.9-signed` artifact's APK over v0.0.8 from [GitHub Actions](https://github.com/demetrecerrone-star/RouteRevive/actions/workflows/android.yml). **Do not uninstall**; local encryption keys are destroyed by uninstalling. Back up with a password first. Android unit tests must pass and build signing secrets must remain unchanged.

## Security / product limitations
Customers' typed details are only transferred when they explicitly hand the message to their SMS/email application. Do not assume any submitted request is confirmed or automatically imported. Android records remain Keystore-encrypted, with owner-approved manual SMS workflows; no hosted authentication service, public API, realtime availability, or automatic messaging has been implemented. The local workspace ID is NOT a security credential.

---

# RouteRevive v0.0.8 — Requests, Reminders & Clean Dashboard

This release keeps all v0.0.7 customer records, encrypted backups, photos,
invoices, financial reporting, business profiles, maps, and route planning.

## New features

- **Responsive dashboard:** compact two-column quick actions, open request count,
  upcoming jobs, invoice balances, and easy access to encrypted full backup/restore.
  No large overflowing button stack.
- **Local booking request inbox:** owner records inquiries from phone, text, email,
  or in person. Review requests, mark contacted/declined, and explicitly confirm
  into a real appointment. The app checks for a future date, time, valid duration,
  and scheduling conflicts. Returning customer matching uses phone number; new
  records have promotional SMS consent **disabled**. Request status and confirmed
  appointment ID are saved together.
- **Manual SMS appointment reminders:** from **Overview → Schedule**, tap
  **Open SMS reminder draft** on a future scheduled appointment. The business
  must review and send the text through their own SMS app. Opening the draft
  does not mark it sent. Tap **I sent the reminder** only after actually sending;
  this stores the date, preventing mistaken repeated reminders on the same day.
  Opted-out customers, demo records, canceled jobs, invalid phone numbers, and
  appointments more than 30 days away cannot receive reminder drafts.
- **Schedule conflict hardening:** no bookings that cross midnight; preceding-day
  overlapping bookings are accounted for.
- Encrypted full backups automatically include booking requests and reminder history.
  Old v0.0.7 encrypted archives and older JSON files remain readable.

## Important limitations

This is a **local owner-managed booking inbox**, not a hosted public booking site:
customers cannot submit requests to the app online yet, and no background SMS
reminders or automatic texts are sent. There is no live SMS delivery confirmation;
the owner must manually record when a reminder was actually sent.
Remember to comply with applicable messaging rules and customer contact preferences.

## Updating Android

Use the permanent signed `RouteRevive-v0.0.8-signed` artifact from
[GitHub Actions](https://github.com/demetrecerrone-star/RouteRevive/actions/workflows/android.yml).
Install `app-release.apk` directly over your existing **signed** v0.0.7. Do
not uninstall; uninstalling erases locally encrypted data and its Keystore key.
Export a full encrypted backup first as a precaution. Android application ID
remains `com.routerevive.app`; versionCode is 8.

---

# RouteRevive v0.0.7 — Business Essentials

New in v0.0.7:
- **Encrypted local data**: on first upgrade, existing app-private JSON records are migrated to Android Keystore AES-256-GCM. The original database is not replaced until new ciphertext is verified. Uninstalling removes the key; a full backup is critical.
- **Encrypted full backup/restore**: the Overview page exports password-protected `.rrb` archives containing JSON records, actual before/after JPGs, and saved business logo. Passwords require at least 10 characters. Archive format uses PBKDF2-HMAC-SHA256 with 210,000 iterations and AES-256-GCM authentication. Restore verifies the entire ciphertext before importing. Passwords cannot be recovered. Backup and photo limits apply.
- **Business profile**: save name, contact details, address, web URL, payment terms and logo. PDFs use the company profile as default branding.
- **Financial reporting**: invoiced totals, payments received, outstanding/overdue issued invoices, and current-month receipts. Campaign payment totals are displayed **separately** because they can duplicate job payments; never add them together.
- **CSV preview/import**: accepts headered CSV containing `name,phone,zip`; optional `service,lastServiceDate,address,notes`. Preview rejects invalid rows and duplicates. Every imported customer has SMS promotional consent disabled; no marketing texts are sent automatically.
- **Optional app launch lock**: use Android system PIN/password/pattern when starting the app, if your phone already has a secure lock. This option is in **Overview → Business essentials → Profile**. Lock is separate from encryption and currently applies to cold launch, not every app switch.
- **Same signing certificate, same Android package** (`com.routerevive.app`), versionCode 7. Install directly over signed v0.0.6 without uninstalling.

## Important privacy and release notes

Old plaintext JSON exports can still be imported with the **Import older JSON** button, but do **not** contain original photo bytes. The new encrypted `.rrb` format is recommended. Exports are user-controlled documents; protect archive passwords and do not place them in shared drives or public repos. Temporary unencrypted ZIP data is generated only inside Android app-private cache and deleted after encryption/decryption.

Photos remain private on the device; opt-in automatic encrypted snapshots include them, and explicit device imports can restore them. A failed or incomplete image file will cause a full backup to fail instead of silently omitting photos. The restore validates file counts, size ceilings, names and all record references before replacing saved records. Retain your original backup until you confirm the restoration works.

No live payment processing, tax calculations, compliance guarantees or fully automated customer SMS. Device-side launch locking is not a substitute for a secure Android device and physical access controls.

Build from [GitHub Actions](https://github.com/demetrecerrone-star/RouteRevive/actions/workflows/android.yml): select artifact `RouteRevive-v0.0.7-signed` and install `app-release.apk` over the installed release. JDK17, Gradle8.10.2, Android SDK35.

---

# Previous release: RouteRevive v0.0.6 — Jobs, Invoices & Custom Icon

RouteRevive now supports the full local service workflow from booking through customer record, job notes, work photos, PDF estimate/invoice and manual payment ledger.

## New in v0.0.6
- **Jobs tab:** every appointment becomes a linked job record. Add line items (service, quantity, unit price), work instructions and business name.
- **Before / after photos:** take photos or import from gallery into app-private local storage. View or delete photos from the job.
- **PDFs:** share estimates and invoices through Android's native share sheet using temporary PDF files and a restricted FileProvider.
- **Payments:** record deposits and final payments already received, view balances and overdue issued invoices. No money is processed by RouteRevive.
- **Custom app icon:** a green landscape with a winding road and leaf, supplied as an adaptive Android launcher icon.
- Existing customer records, marketing-consent rules, appointments, route planning, maps and campaign workflows remain intact.
- The Android package remains `com.routerevive.app` and versionCode increases from 5 to 6; install the signed APK over signed v0.0.5 without uninstalling.

**Backup limitation:** JSON export/import includes job details, line items, photo *references*, payments and invoices, but **NOT the actual photo image bytes**. Private photos remain on the same device during an in-place update; they will be lost if the app is uninstalled or its storage is cleared. Save copies of important photos separately in your gallery. Backup JSON contains customer and financial data and is not encrypted.

**Financial/legal scope:** This prototype generates simple manually managed PDF estimates/invoices. No tax calculations, automated payment processing, refund handling, accounting integrations or legal invoicing guarantees. Verify invoices and amounts before sharing.

## Installation
Download the `RouteRevive-v0.0.6-signed` artifact from [GitHub Actions](https://github.com/demetrecerrone-star/RouteRevive/actions/workflows/android.yml), unzip and install `app-release.apk` directly over your previous *signed* version.

# Previous releases

**Version 0.0.5 — Smart Route Planning (local-first Android app)**

## Version 0.0.5 — Smart Route Planning

- The Map tab's **Opportunities & routes** view includes a daily route planner.
- Scheduled and in-progress appointments appear in booked time order, with customer-by-customer navigation links.
- An optional distance-first preview compares straight-line travel between mapped stops. It **does not** alter bookings or guarantee feasible arrival times. Reschedule explicitly before changing the order you drive.
- On-map job colors indicate scheduled, in-progress, completed, cancelled, and other customers.
- Start or complete work from the route panel; reschedule or cancel bookings from **Schedule**.
- All updates retain the existing on-device data format. Upgrade from the permanently signed v0.0.4 without uninstalling.

**Privacy:** Directions are opened only after confirmation; a third-party navigation app receives coordinates for the selected stop(s). No background customer geocoding or location tracking is introduced.


## Map navigation stability patch (v0.0.4)

- The Map tab has a dedicated, bounded map viewport; all four panning directions and two-finger pinch gestures operate within it.
- "Explore map" shows the map and zoom/recenter controls; "Opportunities & routes" shows independently scrolling customer search, service routes, and address pinning. The map is never nested within the scrolling detail list.
- Marker info windows and zoom controls no longer change the viewport height or obscure adjoining page text.
- Marker overlays only refresh when actual pins/route data changes, not during unrelated Compose recomposition.
- This update uses the same stable release key and increments Android versionCode to 4. Install over the signed v0.0.3 without uninstalling.

## Neighborhoods (introduced in v0.0.3)

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
4. The GitHub workflow runs tests and creates an APK signed with this key. Use the **RouteRevive-v0.0.6-signed** artifact from a successful run.
5. For every update, reuse the **same secrets**, increment `versionCode`, and keep the Android `applicationId` unchanged.

**The previous v0.0.1 and v0.0.4 debug APKs used ephemeral signing keys.** These APKs are not directly upgradable to this new permanent signature. If no important data exists, uninstall the debug APK before installing the newly signed v0.0.4 release once. That creates the stable update path going forward.



RouteRevive helps local pressure-washing and service businesses recontact previous customers and track neighborhood repeat-service offers, without automated bulk messaging.

## Download the Android APK

1. Once signing secrets are configured, open [the Android build history](https://github.com/demetrecerrone-star/RouteRevive/actions/workflows/android.yml).
2. Under **Artifacts**, choose **RouteRevive-v0.0.6-signed** (sign into GitHub if prompted).
3. Download and unzip the artifact, then install `app-release.apk` on Android 8.0 or newer.
4. This is a privately signed testing release, **not yet a Google Play production release**. Android may ask you to allow installation from your files app. Install only if you trust your own repository's build.

GitHub Actions automatically runs unit tests and builds an APK whenever the main branch changes. Debug APKs built on separate runners may use different signing keys and require uninstalling the prior test version; uninstalling deletes locally stored records. Stable release signing and export/backups must be added before using live business data.

## Features (v0.0.5)

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
- Because v0.0.1's APK came from an ephemeral GitHub Actions runner debug key, v0.0.4 might not install over v0.0.1. If Android reports an incompatible signature, **uninstalling v0.0.1 deletes its data, and v0.0.1 has no export feature**. Do not uninstall if you need those records; preserve them or contact the developer about data extraction before changing versions.
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

- v0.0.4: basic customer editing and search, scheduling, JSON export/import (this version).
- v0.0.4: CSV import, encrypted storage, more flexible appointment time windows, advanced optional driving-route integration.
- v0.0.4: business accounts, multi-device secure sync, stable signing and recoverable data.
- Later: service-area maps, self-service booking pages, compliant opt-in SMS provider, subscription billing.

**Do not contact real people until you have a legally suitable consent and privacy process in place.**


## v0.2.1: Optional automatic cloud backups

Go to More → Private cloud backups → section 4 after signing in. Select Daily or Weekly and enter a 10+ character shared **backup password**. It is protected with Android Keystore on this device, never sent to Supabase, and required on any other phone. A scheduled WorkManager job uploads an AES-GCM encrypted snapshot only when records have changed and it is safe to do so. The existing Supabase bucket SQL is unchanged.

For a second phone, connect the same cloud account, enable automation with the SAME backup password, and select **Check & sync now**. If a newer cloud version exists, you must explicitly confirm replacing the data on that phone. Conflicting local changes cannot be merged automatically; export each device and choose a winner manually. Background work will NEVER silently replace locally edited records. Details: [Supabase setup](docs/SUPABASE_SETUP.md).
