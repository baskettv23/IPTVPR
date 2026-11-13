# T-028 – Automatic IPTVPR releases and stable signing

## Goal

Make every push to `main` produce a downloadable, installable APK and let the in-app updater find it.

## Changes

- GitHub repository renamed from `IPVIO-Stream-ColorDebrid` to `IPTVPR`.
- `.github/workflows/build.yml` now:
  - derives `VERSION_CODE` and `VERSION_NAME` from the Actions run number,
  - builds the debug APK with those values,
  - renames it to `IPTVPR.apk`,
  - writes `update.json` with the SHA-256,
  - uploads the artifact `iptvpr-debug`,
  - creates a published GitHub release tagged `v<versionName>` with `IPTVPR.apk` and `update.json`.
- `AppUpdate.kt` points to `baskettv23/IPTVPR`.
- `app/build.gradle.kts` accepts `-PVERSION_CODE` / `-PVERSION_NAME` and signs debug builds with the release key when a keystore is present.
- The stable PKCS#12 signing key is stored only in repository secrets:
  `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
  `SIGNING_KEY_PASSWORD`, `SIGNING_STORE_TYPE`.
- Local backup of the signing material: `~/.iptvpr-signing/` (not committed).

## Important

- The first APK signed with the new key cannot be installed over an older APK signed with a different key. Uninstall the old `com.ipvio.stream.test` once, then install the new APK.
- After that, pushes to `main` create newer `versionCode` values and the in-app updater can install them over the existing app.
- Do not lose the signing key. Losing it forces every user to uninstall and start over.
- `update.json` is generated in CI and is not committed.

## Acceptance

- Push to `main` creates a release with `IPTVPR.apk` and `update.json`.
- The APK is signed with the same certificate on every run.
- The installed app detects the newer `versionCode`, downloads the APK, verifies the SHA-256 and signer, and opens the installer.
- Existing playlists, favorites, history and settings survive the update.
