# RouteRevive v0.2.1 — Private cloud backups and guarded device sync

RouteRevive continues working offline without Supabase. Cloud backups cannot work until the business creates a Supabase project, adds a private Storage bucket, and configures the app.

## 1. Create a project

1. Go to https://supabase.com/dashboard and create a project.
2. Under Authentication → Providers → Email, enable email/password sign-in and email confirmation. Configure SMTP delivery as needed.
3. Open SQL Editor and run the security SQL below.
4. Find the HTTPS Project URL and **public publishable/anon key** in your project's API settings. Enter these in RouteRevive under **More → Private cloud backups**.

**NEVER enter the service_role key, secret key (sb_secret_), personal access token, JWT signing secret, database password or SMTP password in the Android app or GitHub.** Public Supabase keys are not secrets. The app rejects common secret key patterns.

## 2. Create a private Storage bucket with Row Level Security

Execute this SQL in the trusted Supabase SQL Editor:

~~~sql
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'route-revive-backups', 'route-revive-backups', false,
  52428800, ARRAY['application/octet-stream']
)
ON CONFLICT (id) DO UPDATE SET
  public = false,
  file_size_limit = 52428800,
  allowed_mime_types = ARRAY['application/octet-stream'];

DROP POLICY IF EXISTS "RouteRevive read own backups" ON storage.objects;
DROP POLICY IF EXISTS "RouteRevive insert own backups" ON storage.objects;

CREATE POLICY "RouteRevive read own backups"
  ON storage.objects
  FOR SELECT TO authenticated
  USING (
    bucket_id = 'route-revive-backups'
    AND (storage.foldername(name))[1] = (select auth.uid())::text
  );

CREATE POLICY "RouteRevive insert own backups"
  ON storage.objects
  FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'route-revive-backups'
    AND (storage.foldername(name))[1] = (select auth.uid())::text
    AND (storage.filename(name)) LIKE 'backup-%.rrb'
  );
~~~

Only the account holder can read or insert archives inside their own UUID-named folder. The bucket remains PRIVATE and is never publicly readable. There are intentionally no UPDATE/DELETE policies; each upload creates a timestamped immutable archive. Old archives may accumulate and incur storage charges; they can be managed through the trusted project admin dashboard, or via a later reviewed deletion feature.

The example bucket limits each archive to 50 MiB; this is compatible with typical Supabase free project limits. Larger backups require a supported plan and a higher bucket limit. The Android client has an independent 165 MiB safety limit. **Never disable RLS or make the bucket public to fix an upload error.**

## 3. Use RouteRevive

1. Install the same-signed v0.2.1 APK **over** the existing app; do not uninstall.
2. Navigate to More → Private cloud backups.
3. Enter your project URL (https://PROJECT.supabase.co) and public publishable/anon key.
4. Register with a business email and password, verify the email if asked, then sign in.
5. Create a cloud snapshot by entering a **separate 10+ character encrypted backup password**. RouteRevive encrypts all records and photos ON YOUR PHONE and uploads only the encrypted archive.
6. Refresh your cloud backup history.
7. On another phone, connect to the same project, sign in to the same account, refresh history and choose a snapshot.
8. **WARNING:** Restoring REPLACES all records on that phone, including customers, appointments, photos, jobs, payments, campaigns, requests and business profile. Unsynced local edits will be lost. Create a separate local encrypted backup first.

Cloud archives are authenticated AES-256-GCM with PBKDF2-HMAC-SHA256 key derivation (the existing RRB7 format). For MANUAL backups the archive password is not saved. If you explicitly enable automatic backups, your shared backup password is stored only on that Android device in Keystore-encrypted preferences; Supabase never receives it. The password cannot be recovered on a replacement phone. Supabase Auth manages account credentials, and RouteRevive stores only the refresh credential under an Android Keystore encryption key. Private Storage RLS uses the signed-in user's JWT.

### Important limitations

Manual encrypted backup/restore remains available. In v0.2.1 you may **opt in** to daily or weekly automatic encrypted snapshots, plus confirm importing newer snapshots onto another phone signed in to the same cloud account. The Android OS schedules background work when network service is available; exact times are not guaranteed. Only edited records trigger a new automatic snapshot. Automatic snapshots use the same existing bucket security policy and a reserved marker inside the original `backup-...rrb` filename format; **no SQL migration is required**.

To set up a second device, install v0.2.1, connect to the same Supabase project, sign in to the SAME account, then choose a backup password **identical to the first phone's automatic backup password** under section 4 of the Cloud page. Press **Check & sync now**; if newer cloud data exists, review the warning and confirm **Import cloud snapshot**. Imported records replace local records on that device, including images. Export a separate local encrypted backup before importing.

**This is not simultaneous multi-device live editing.** Incoming changes are NOT restored in the background. If both phones have changed data since their last common snapshot, syncing pauses rather than silently overwriting either copy. Manual snapshot restore remains a deliberate destructive recovery option, not an automatic merge. Keep only one device editing at a time and import latest changes on the other first. There is no shared team account, hosted public booking, or real-time appointment update system.

Photos are included inside the encrypted archive, while Supabase Auth processes account/email metadata under your project's terms. Temporary encrypted files are deleted from app-private cache after transfers. Local editing remains available without network access.

### Troubleshooting

- **403:** Check project URL, bucket name, policies and email-verified login; never make the bucket public.
- **404:** Create the private bucket by running the SQL.
- **413:** File exceeds bucket size or plan limit; reduce photo sizes or upgrade supported limits.
- **Email verification:** Check inbox, spam and Supabase SMTP configuration.
- **Wrong archive password:** Use the separate password from when that snapshot was uploaded.
- **Automatic backup pending:** Android may defer periodic tasks to save battery or wait for internet; use **Check & sync now** while online.
- **Two phones contain different data:** The app refuses automatic conflict resolution. Save a separate export of BOTH phones; decide which complete snapshot to restore manually.
- **Forgot the automatic backup password:** Existing snapshots cannot be decrypted without it. Disable automatic backup and carefully migrate or restore a known-good archive before re-enabling. Never lose the recovery password.

References: https://supabase.com/docs/guides/auth | https://supabase.com/docs/guides/storage/buckets/fundamentals | https://supabase.com/docs/guides/storage/security/access-control
