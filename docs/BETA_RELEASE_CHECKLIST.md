# RouteRevive 0.1.0 — Beta release checklist

**Status:** internal/beta candidate only. Passing a build does not mean Google Play has approved this application.

## Optional cloud gate (v0.2.0)

- [ ] Create a dedicated Supabase project with verified email delivery.
- [ ] Execute docs/SUPABASE_SETUP.md Storage SQL. Confirm PRIVATE bucket and user UUID folder RLS.
- [ ] Test email signup, confirmation, sign-in, session refresh and sign-out.
- [ ] Verify only PUBLIC anon/publishable key is entered. Never use a secret/service_role key.
- [ ] Upload a password-encrypted archive, list it, restore on a second test phone.
- [ ] Try a wrong backup password, expired session, disconnected network, wrong project, and failed storage permissions.
- [ ] Ensure cloud restore warning appears and unrelated local records are not silently overwritten.
- [ ] Document cloud account/metadata processing in privacy policy and Play Data safety forms.
- [ ] Decide cloud archive retention, deletion and storage costs. Snapshots are immutable in v0.2.0.

## Build/distribution

- [x] Keep Android application ID \`com.routerevive.app\`, stable signing keystore and increment versionCode to 10.
- [x] Target Android API 36 for new phone/tablet Google Play submissions from August 31, 2026.
- [x] Build a signed release **APK** for direct test updates AND a signed **AAB** (Android App Bundle) for Play Console.
- [ ] Verify both artifacts' certificate fingerprints and API level.
- [ ] Run unit tests and Android release lint in GitHub Actions; install the release on at least two physical devices.
- [ ] Install over signed v0.0.9 **without uninstalling**, confirm records/maps/photos/payments intact.
- [ ] Test Google Play internal track first; upload AAB only after owner verifies store listing and privacy documents.
- [ ] Turn on Play App Signing as required; retain private upload key offline. Never commit signing keystores or passwords.
- [ ] Check Play Console pre-launch reports and fix crashes, ANRs and accessibility concerns.

## Customer/financial data

- [x] Local data audit checks orphaned records, invalid appointments, overlapping jobs, missing photos, and invalid payment totals.
- [x] Validate backup JSON before replacing live encrypted storage; full restore prevents photo filename conflicts.
- [ ] Test \`.rrb\` backup created on device A and restored on clean device B with photos, logo, job payment records, requests and campaign exclusions.
- [ ] Test wrong password, truncated archive, modified archive, corrupted photos, insufficient device storage and import cancellation; verify existing data stays unchanged.
- [ ] Confirm opt-outs cannot be overridden and imported customers have promotional SMS consent disabled.
- [ ] Review legacy plaintext JSON exports previously created; delete or secure them.
- [ ] Use fictional records until security, retention and messaging compliance have been assessed by the business.

## Complete workflow smoke test

- [ ] Create customer / CSV import; verify marketing consent defaults OFF.
- [ ] Record new booking request, review availability, approve appointment.
- [ ] View job in Schedule and Map; verify map pan in every direction.
- [ ] Record service line items, job photos, complete service.
- [ ] Generate estimate/invoice PDF and verify correct business identity, amounts and layout.
- [ ] Record a deposit and final payment; totals and overdue statuses must not double count legacy campaign payments.
- [ ] Open an SMS reminder draft; verify it does not mark sent automatically.
- [ ] Mark a customer opted out; confirm marketing and reminder sends are blocked.
- [ ] Export encrypted backup; restore on second test device; check photos and totals.
- [ ] Force close/restart; run the on-device readiness audit again.

## Google Play app-content requirements (October 2026 review)

- [ ] Prepare and publish an **accurate privacy policy** at a public, non-PDF, permanent HTTPS URL. Include a link/text inside the app. The draft in this repo needs verified support contact and review.
- [ ] Complete Google Play's **Data safety** form based on actual SDK and app behavior (map tiles, address geocoding, external SMS/email/maps and user-selected backup locations). Do not mark "no collection" without reviewing network calls and third-party behavior.
- [ ] Verify target audience, content rating, app access instructions, ads declaration, data deletion and any other applicable forms.
- [ ] Confirm feature graphic, app icon and phone screenshots portray actual tested app screens.
- [ ] If using a new personal developer account registered after 2023-11-13, check whether the required closed test of **at least 12 opted-in testers for 14 continuous days** applies before production access.
- [ ] Confirm developer identity and contact information in Play Console.
- [ ] Audit third-party libraries, security advisories, licensing, OpenStreetMap attribution and tile server acceptable-use requirements.
- [ ] Test Android 16 edge-to-edge behavior and back navigation on production devices.

## What this release still does NOT provide

There is no hosted booking website, online account system, cloud sync, payment gateway, automatic SMS send, delivery confirmation, or statutory tax calculation. RouteRevive remains local-first. No guarantee of legal compliance, platform approval or readiness for broad commercial use is implied.

## Official references

- Target SDK 36 (August 31, 2026): https://support.google.com/googleplay/android-developer/answer/11926878
- New personal-account testing: https://support.google.com/googleplay/android-developer/answer/14151465
- User data / privacy policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Android App Bundles: https://developer.android.com/guide/app-bundle
