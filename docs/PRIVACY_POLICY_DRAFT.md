# RouteRevive Privacy Policy — DRAFT / NOT YET PUBLISHED

**NOT READY TO USE AS A PUBLIC POLICY.** Before release, replace the placeholders, verify every statement against the final tested Android App Bundle and third-party SDK behavior, and host a non-editable HTML page at a publicly accessible HTTPS URL. The policy must match the final Google Play Data safety questionnaire and any regional privacy obligations.

**App:** RouteRevive (Android; package \`com.routerevive.app\`)
**Publisher / Data controller:** [REPLACE WITH CORRECT LEGAL DEVELOPER OR COMPANY NAME]
**Privacy contact:** [REPLACE WITH VERIFIED SUPPORT EMAIL OR CONTACT FORM URL]
**Effective date:** [REPLACE ON PUBLICATION]

## What the app does

RouteRevive is a local-first service-business organizer. A business owner can enter contact and service history, appointments, job notes, before/after photos, estimates, invoices, payment records, booking requests, and SMS marketing preference evidence. The owner is responsible for providing accurate information about customers and for using legally sufficient consent and communications practices.

## Data you enter or create

Data can include customer name, phone, street address, ZIP, approximate mapped coordinates, service dates and types, marketing-consent evidence and opt-out state, job descriptions, photos, pricing and payment records, correspondence drafts, appointment reminders and business identity. RouteRevive does not connect to a credit card processor or bank. Payment entries are manually recorded by the business and cannot be considered proof of payment.

## Storage and encryption

The app keeps business/customer records inside its Android app-private data, encrypted using a device-managed Android Keystore key with AES-GCM. Before/after photos and the business logo are stored as app-private files, separate from that encrypted record store. These photos are not individually Keystore-encrypted; access depends on Android app sandbox protection and device security.

Starting with v0.2.0, the app supports optional business login using a Supabase project the business owner configures. Supabase may process the account email, login metadata, refresh credentials and private Storage object metadata under that project and its privacy terms. The app can upload an **end-to-end password-encrypted full backup archive** (including customer records, financial data, and job photos) when the owner chooses Upload, or on a daily/weekly schedule after separately enabling automatic encrypted backups. Supabase receives the encrypted archive, not plaintext customer records. Restoring a cloud snapshot is a deliberate user action that replaces the current local dataset after password verification. There is no automatic per-edit live synchronization, shared team account or background SMS sending. Automatic backups require opt-in and do not import other devices' data without confirmation. RouteRevive does not include a behavioral analytics SDK. The app does not directly collect precise live device GPS location.

## External services and chosen actions

- **Map browsing:** OpenStreetMap-based map services provide visual map tiles, receiving the map area the user chooses to view and technical network data as required to serve tiles. Users should also review the map provider's terms and privacy practices.
- **Optional address lookup:** Android's geocoding provider may receive a selected customer's street address to look up a map pin.
- **Driving directions:** When the owner opens navigation, the chosen coordinates are passed to an external maps application/service (e.g. Google Maps). The maps provider's privacy policy then applies.
- **SMS and email:** Sending a promotional SMS or appointment reminder is always a user-confirmed action through the owner's installed SMS app. Customers can use a separately shared static HTML form to prepare an SMS or email inquiry. These external services may receive the contact information and request content selected by the user. The app itself does not confirm delivery.
- **Sharing files:** When the owner shares PDF documents, photos, or an encrypted backup, the selected app/provider receives the content the owner authorizes.
- **Full backups:** An optional export contains local records and actual job photos. The archive uses password-based key derivation and authenticated AES-256-GCM encryption, and the owner chooses where it is stored. Passwords cannot be recovered by the app.
- **Legacy backups:** Older optional JSON backups may contain customer information in *unencrypted plaintext*. Users should treat them as sensitive and delete insecure copies once safely migrated.

**Before publishing:** Check the final app dependency list for all additional SDK traffic, technical identifiers, and any applicable disclosures; revise this section if needed.


## Optional Supabase cloud account and backup

Cloud is OFF until a business owner supplies their own Supabase project URL and public anon/publishable API key, creates an account and chooses to upload an encrypted archive or enables scheduled encrypted backups. Supabase Auth processes account email/password authentication and related logs; RouteRevive persists the refresh credential under a separate Android Keystore key and does not save the account password. Supabase private Storage holds encrypted archive bytes and object metadata under account-scoped Row Level Security policies. The owner can restore or sign out. Manual archive passwords are not saved. When the owner enables automatic backups, the chosen shared archive password is encrypted using Android Keystore on that device to enable unattended uploads; it is not uploaded to Supabase and cannot be recovered after device loss. Scheduled backups can be stopped from the Cloud screen, but stopping does not delete previously uploaded snapshots. Incoming synced snapshots only replace local records after explicit confirmation. No automatic conflict merge occurs. Deleting Supabase account data, storage archives and related provider logs must be handled through the project administrator and provider according to legal responsibilities; merely uninstalling the Android app does not remove cloud storage. The project administrator must configure retention and deletion procedures and should not use a publicly accessible storage bucket.

## Marketing and appointments

Imported customer records start with promotional SMS permission disabled. For manually entered records, the business is responsible for verifying permission before marking it as documented. Recorded opt-outs block promotional sends and app-generated SMS reminder drafts. The user must approve and send each SMS using their own installed messaging app. No automatic or bulk SMS campaign delivery is performed.

The customer booking request page prepares inquiries, not actual confirmed calendar reservations. The owner must manually process and confirm appointments in the app.

## Retention and deletion

Data remains in the app's private storage until the device owner edits records, clears app data, or uninstalls RouteRevive, subject to user-created backups and files shared to other apps. Uninstalling the app removes the Android Keystore key and app-private data, so back up first if records should be retained. The user is responsible for protecting and removing exported backups stored outside RouteRevive. The app does not currently offer a hosted account or remote account deletion because it has no online customer accounts.

**Before publishing:** Confirm exact file retention behavior, whether there is a usable per-record deletion interface, and whether any retention/deletion notices or mechanisms are required in applicable jurisdictions. Do not claim in-app account deletion for a service that does not exist.

## Data security and rights

Protect the phone with its screen lock and consider enabling RouteRevive's optional launch lock. Use a strong unique password for the full backup and store it securely. Device compromise, exposed cloud storage, or sharing a backup password can still expose sensitive information. Depending on local law, business owners may need to provide their customers access, correction, or deletion opportunities. Owners should handle such requests as the controllers of their own customer records.

## Children and changes

RouteRevive is intended for adult business operators, not for children. The developer should update this policy before releasing features that change data handling, especially cloud accounts, hosted booking pages, analytics, or payment processing.

## Contact

[REPLACE WITH VERIFIED SUPPORT CONTACT AND, WHERE NECESSARY, PHYSICAL/LEGAL CONTACT DETAILS]

---

**Publisher checklist:** Host this policy as public, readable, non-editable HTML; add its HTTPS URL to Play Console; provide the same link in the app; verify current Google Play requirements and the final app behavior. Review third-party service privacy notices and local legislation before publication.
