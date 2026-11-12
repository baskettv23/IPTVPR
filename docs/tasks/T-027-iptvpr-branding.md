# T-027 – IPTVPR branding

## Goal

Rename the user-visible app to **IPTVPR** and replace the launcher and in-app marks with a Puerto Rico flag design.

## Affected files

- `app/build.gradle.kts`: `appLabel` = `IPTVPR` (default and debug).
- `app/src/main/res/values/strings.xml`: `app_name` = `IPTVPR`.
- `app/src/main/res/drawable/ic_launcher_background.xml`: navy radial gradient.
- `app/src/main/res/drawable/ic_launcher_foreground.xml`: renders the new badge (`ic_logo_fg.png`).
- `app/src/main/res/drawable/ic_logo_fg.png`: adaptive foreground (432 px).
- `app/src/main/res/drawable/ic_brand_mark.png`: in-app mark (512 px).
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`: themed icon (star with play cutout).
- `app/src/main/res/mipmap-*`: square and round legacy icons.
- `app/src/main/res/mipmap-anydpi-v26/*.xml`: monochrome layer.
- `AddPlaylistScreen.kt`, `SettingsHub.kt`, `PhoneImport.kt`, `RemoteLogic.kt`, `Donate.kt`, `ImportFiles.kt`, `AppUpdate.kt`, `Round17Logic.kt`: visible name and update address.
- `DictDebrid.kt`: translations for the renamed UI texts.
- `design/iptvpr_logo.svg`, `design/legacy_square_bg.svg`, `design/legacy_round_bg.svg`: source artwork.

## Design

A circular navy badge with a gold ring and a cyan inner ring. Inside, the Puerto Rico flag is tilted slightly for a dynamic look: five red/white stripes, the blue triangle pointing right like a play symbol, and a glowing white star. Two small gold/cyan sparkles complete the mark.

## Acceptance criteria

- The launcher shows the name **IPTVPR** and the new icon.
- The in-app brand mark (sidebar, loading screen, add-playlist header) uses the new artwork.
- The About page and the main branding texts say IPTVPR.
- The update check points to `baskettv23/IPVIO-Stream-ColorDebrid`, not the upstream project.
- Existing storage keys, package name and user data are unchanged.

## Risks

- The adaptive foreground keeps the badge inside the 61 % safe zone; extreme launcher masks may still crop the outer glow.
- The debug build keeps `applicationIdSuffix = ".test"`, so it installs next to the original app.

## Test steps

1. Install the debug APK on a TV/phone: the launcher shows **IPTVPR** and the flag badge.
2. Open the app: sidebar, loading screen and add-playlist header show the new mark.
3. Settings > About: title says IPTVPR.
4. Android 13+ themed icons: the monochrome star/play silhouette is used.
5. Check that the old app (if installed) and IPTVPR can coexist.

## Status

Artwork and rename implemented. The GitHub Actions debug build verifies compilation and packaging; no local build or device test was possible.
