# T-026 – Colorful UI and user-provided debrid streams

## Goal

1. Give the detail page actions, the sidebar menu and the filter pills colored accents without changing focus behavior.
2. Add optional Real-Debrid and TorBox API keys and a "Stream" action that resolves a magnet link or `.torrent` address entered by the user.

The app still ships no sources and no search. The Stream action only resolves links the user pastes in, for content the user has the rights to.

## Affected files

- `Debrid.kt` (new): preferences, key check, Real-Debrid/TorBox resolve logic, file selection.
- `DebridDialog.kt` (new): TV-friendly dialog with service choice, link field, status and play action.
- `DictDebrid.kt` (new): German keys with English and Spanish rows.
- `Lang.kt`: load `DictDebrid`.
- `DetailScreen.kt`: tinted actions, Stream button, dialog.
- `HomeParts.kt`: `HeroButton` tint parameter.
- `Dropdown.kt`: `FilterPill`/`DropdownPill` tint parameter and icon color mapping.
- `Sidebar.kt`: per-menu color.
- `SettingsHub.kt`: debrid fields, check and save in `ApiKeysPage`.
- `MainActivity.kt`: `DebridPrefs.init`.
- `DictConsistencyTest.kt`: include `DictDebrid` in the placeholder check.

## Storage

New SharedPreferences file `ipvio_debrid` with keys `realdebrid` and `torbox`. No existing key or file is renamed.

## Acceptance criteria

- Menu icons and filter pills show distinct colors; focused elements keep the white focus style.
- Detail page buttons keep their labels and actions, with colored icons and a subtle colored background.
- With no debrid key, the Stream dialog explains where to add one.
- With a key, a magnet link resolves to a direct URL and opens in the player.
- A `.torrent` URL is downloaded (max 5 MB) and handed to the service.
- The service file selection prefers a video file, then a Spanish-audio name, then size.
- Keys are stored on the device only and are never logged.

## Risks

- Debrid APIs and response shapes can change; errors are shown as text.
- Long torrent preparation is bounded to 180 s.
- The new preference file is not part of the existing backup. Undecided; do not add it without a migration decision.

## Test steps

1. Settings > API keys: enter a Real-Debrid token and/or TorBox key, press "Speichern und prüfen"; a valid key shows "Schlüssel gültig", a wrong key shows an error.
2. Open a movie detail page, press "Stream"; paste a magnet link for content you own/are allowed to use and press "Abspielen". The status text updates and the player opens the direct URL.
3. Remove both keys; the Stream dialog points to Settings.
4. Check the sidebar and filter pills on a TV device: colors visible, focus still white.

## Status

Implemented in the working tree. Not compiled or device tested locally (no JDK/Android SDK in the assistant environment); verification is the GitHub Actions debug build.
