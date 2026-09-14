# MEMORY

## [13] GitHub publication
- Scope: publish the current `devtest` project to `kbo3551/CodexCLI_App`.
- Change: initialized an independent `main` repository; set GitHub `origin`; build outputs remain ignored.
- State: remote is empty; secret-pattern scan clean; 56 Maven tests pass.
- Next: commit, push `origin/main`, and compare local/remote SHA.

## [10] The @ picker never opened - caret lag
- JavaFX fires `textProperty` listeners BEFORE moving the caret. On the first keystroke the text is
  "@l" while the caret is still 1, so the mention fragment came out empty and the popup was hidden.
  The caret listener that would fix it was gated on `isShowing()`, so nothing ever opened it
- Fix: caret listener always re-evaluates. Parsing lives in `ui/MentionParser` (pure, 8 tests)
- `ComposerMentionWiringTest` drives a real Composer in a Scene, finds the input via
  `lookup(".composer-input")` and sets text, asserting the search actually runs. Use this instead of
  SendKeys: synthetic keys never reached the app window and made the first diagnosis inconclusive
- Picker modes: All results / Files only, switched with left/right; re-filters cached results, no
  extra request. CLI's Plugins source has no equivalent here and is deliberately absent
- Startup did not apply `debugLogging` to the logback `codex.wire` level (only saving settings did),
  so raw protocol logging looked broken. `CodexDesktopApplication.applyDebugLogLevel` is now called
  at startup and MainWindow delegates to it
- Tests: 56

## [11] Resizable panels + office quality pass
- Layout is now two SplitPanes: rootSplit[sidebar | centre], centerSplit[chat | office | changes].
  SplitPane has no hidden state, so panels are added/removed on toggle; `addSplitItem` sets the
  divider from the panel's pref width. Divider styling lives under `.content-split`
- Office map is 16x14: 8 desks + a 4-seat meeting table. `seatFor(index)` assigns desks first, then
  meeting chairs, then the lounge, so extra threads still get a seat
- Characters have faces (state-driven expressions), 4 hair styles, optional glasses, 8 palettes, a
  4-frame walk; desks carry laptops (lit while typing). Props: whiteboard, water cooler, shelves,
  cabinet, plants, clock, picture, rug, window with sky and blinds
- Two rendering traps fixed: the seated figure must sit low enough to clear the desk, and the lit
  laptop must be drawn BEFORE the worker or it paints over the face
- Sizing: map width 16 tiles = 256 px, so 2x needs 512 px -> docked panel min 530 / pref 560, and the
  detached window opens at 792x764 for an exact 3x
- `AppSettings.withRuntime/withPolicy/withLanguage` exist so adding a settings field no longer breaks
  positional constructor calls in tests
- Tests: 56
