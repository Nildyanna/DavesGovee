# Releasing & self-update

The app updates itself from this repo's **GitHub Releases**. `UpdateRepository`
reads `/releases/latest`, compares the release tag to the installed
`versionName`, and installs the attached `.apk`. The `.github/workflows/release.yml`
workflow builds and publishes that APK automatically on a version tag.

## One-time setup: signing key

Every release APK must be signed with the **same** key — Android refuses to
install an update signed by a different key. Generate one keystore and reuse it
forever:

```bash
keytool -genkey -v -keystore release.keystore -alias davesgovee \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep `release.keystore` safe and **out of git** (it is gitignored). Losing it
means users must uninstall/reinstall to take future updates.

## One-time setup: GitHub secrets

Add these under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.keystore` output |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | the alias above (e.g. `davesgovee`) |
| `KEY_PASSWORD` | key password |

The repo must be **public** so the app can read releases without a token.

## Cutting a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
   (the tag must be numerically newer than the shipped `versionName`).
2. Commit, then tag and push:

   ```bash
   git tag v1.1
   git push origin v1.1
   ```

3. The workflow builds the signed APK and attaches it to a new `v1.1` release.
   Existing installs pick it up via "Check for Updates" (or the silent check on
   next launch).
