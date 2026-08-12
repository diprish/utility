# Google Drive backup — one-time setup

The app backs up your meters, readings and photos to **your** Google Drive using
the least-privilege `drive.file` scope (it can only touch files it creates — it
cannot see the rest of your Drive). No backend or server is involved; the app
talks to Drive directly from your phone.

Before it works you must create OAuth credentials in Google Cloud and register
the app's signing fingerprint. This is a one-time, ~10 minute task. There is
**no client ID to paste into the code** — the app is identified purely by its
package name + signing certificate, so once the fingerprint is registered the
existing APK just works.

## Steps

1. **Create / pick a project** at <https://console.cloud.google.com>.

2. **Enable the Drive API**
   APIs & Services → Library → search "Google Drive API" → **Enable**.

3. **Configure the OAuth consent screen**
   APIs & Services → OAuth consent screen.
   - User type: **External** (this is fine for personal use).
   - Fill in app name and your email where required.
   - **Scopes**: you can leave the defaults; `drive.file` is non‑sensitive and
     needs no verification.
   - **Test users**: add your own Google account. While the app is in "Testing"
     mode only listed test users can sign in — which is all you need. (You'll
     see an "unverified app" notice on the consent screen; that's expected for a
     personal app and you can proceed.)

4. **Create the Android OAuth client**
   APIs & Services → Credentials → **Create Credentials → OAuth client ID** →
   Application type **Android**.
   - **Package name:** `com.diprish.utilitymeter`
   - **SHA‑1 certificate fingerprint:** see below.

5. Done. Open the app → the **cloud icon** (top‑right of the home screen) →
   **Back up now**, sign in with your test-user account and grant access. Then
   turn on **Daily automatic backup** if you want it hands-off.

## Which SHA‑1 to register

The OAuth Android client is tied to the certificate that signed the APK.

- **Using the prebuilt debug APK from this project** (the one shared with you),
  register this fingerprint:

  ```
  45:FB:20:3E:5A:17:7E:91:30:95:23:1B:03:95:A5:C1:1B:7B:B8:7F
  ```

- **Building it yourself** in Android Studio (your machine has its own debug
  key), register your own instead — get it with either:

  ```bash
  ./gradlew signingReport        # look for the debug variant's SHA1
  # or
  keytool -list -v -keystore ~/.android/debug.keystore \
    -alias androiddebugkey -storepass android -keypass android
  ```

You can register **multiple** Android OAuth clients under the same project (one
per fingerprint), so both the shared APK and your local builds can work at once.

- **Publishing a release build later:** create another Android OAuth client with
  your release keystore's SHA‑1 (and, if you use Play App Signing, the SHA‑1
  Google shows in Play Console → App integrity).

## Notes

- Backups overwrite a single file named `utility-meter-backup.zip` in your Drive,
  so they don't pile up. **Restore** replaces all local data with that file.
- Automatic backup runs about once a day when there's a network connection, and
  only after you've connected once (it reuses the granted access silently).
- To distribute the app to other people (not just yourself), you'd move the
  consent screen from "Testing" to "In production"; `drive.file` does not require
  Google's security assessment, so this is straightforward.
