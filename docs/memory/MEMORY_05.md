# MEMORY

## [7] Model/effort, slash commands, font setting, white-settings fix
- `model/list` efforts live under `reasoningEffort` (objects with a description), NOT `effort`/`id`.
  Reading the wrong key gave an empty list, so the picker showed no reasoning levels at all
- Settings page went white because Modena paints `.scroll-pane > .viewport` with its light
  `-fx-background`; base.css now clears the viewport globally (only sidebar/conversation had it)
- Diagnose UI colour issues by measuring pixels in `target/ui-preview/*.png`, not by eye
- `StylesheetTest` parses every stylesheet with `javafx.css.CssParser` and asserts zero errors plus
  that every `-c-*` token base.css uses exists in both themes. Run it after touching CSS
- Slash palette: `SlashCommand` + `SlashCommandPopup` (Popup + ListView, not ContextMenu, which
  would steal keyboard focus). Composer forwards Up/Down/Enter/Tab/Esc. Commands map onto existing
  actions: /new /model /compact /diff /status /office /settings /stop /help
- Interface font is selectable (`AppSettings.uiFont`, `FontLoader.availableUiFonts()` filters to
  installed families) and applied as a root inline style; `.mono`/code rules keep code monospaced
- Detached office opens at 744x836 so the 15x15 room lands on an exact 3x zoom
- Tests: 40

## [8] @ file mentions
- Backed by `fuzzyFileSearch` (params: query, roots[], cancellationToken). Results carry an absolute
  `root` plus a project-relative `path`, `file_name` and `match_type` (file|directory); ~150 ms
- `CodexSessionService.searchFiles` delegates to the server instead of walking the tree from
  Windows: it is already indexed, honours ignore rules, and returns WSL paths a mention can use
- Composer detects an `@token` before the caret (word start only, so emails are ignored), debounces
  140 ms and drops stale answers by sequence number
- Picking inserts `@relative/path ` and records the mention separately; on send only mentions whose
  token is still in the text are sent as `mention` UserInputs, so deleting the text drops it
- `MentionPopup` mirrors `SlashCommandPopup` (Popup + ListView, never ContextMenu: focus theft)
- Tests: 44

## [9] Picker layout + properties gotcha
- `MentionPopup`/`SlashCommandPopup` share a CLI-style layout: `›` marker, mono name column sized
  from the longest entry so paths align, dim folder column, right-aligned Dir/File tag, hint footer.
  Shared CSS lives under `.picker-*`
- Appending to a .properties file without a trailing newline glues two entries into one line and
  both keys break. `I18nTest` now fails if any value contains '=' and if the two bundles' key sets
  differ. Prefer editing properties with the write tool, not shell append
- `CodexFileSearchIntegrationTest` verifies @ search against the real app-server without spending
  model quota (no turn is started) - use this pattern for other request-only protocol checks
- Tests: 46