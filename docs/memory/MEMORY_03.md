# MEMORY

## [3] Agent office view added
- Scope: `ui/office/` - AgentWorker (state machine), OfficeMap (24x13 char layout, 18 seats
  derived from `=` runs), OfficeRenderer (procedural pixel art on Canvas, 16px tiles, integer 3x
  scale, labels drawn in screen space), AgentOfficeView (CodexSessionListener)
- Behaviour: `turn/started` -> walk to desk; activity -> sit and type with a speech bubble;
  approval request -> marker above head; `turn/completed` -> done/stopped/failed. Hover shows the
  thread name, click opens that thread
- Perf: AnimationTimer runs only while visible AND something is moving; `renderFrameNow(seconds)`
  exists for the headless preview, which has no JavaFX pulse
- Entry point: office toggle button in the MainWindow top bar; `officeView.setThreads(list, activeId)`
  is fed from the `thread/list` result
- UI review loop: `mvn test -Dgroups=preview -Dsurefire.excludedGroups=none` renders
  `target/ui-preview/office-ko.png`; five defects were found and fixed this way
- State: done, 23 unit tests green, package rebuilt (93.4MB)

## [4] UI/UX fix pass after real-run review
- Office is a 540px panel inside centerRow (chat left, top bar visible, panel has an X);
  map is 15x15 for a 2x zoom; agents named `Agent N` by desk with the title on hover and a
  seated/overflow legend
- Composer white dots were Modena's layered `-fx-box-border`; base.css now ends with a
  `.composer-input` override block that must stay last. Verified by counting bright pixels: 4 -> 0
- Settings uses a GridPane (label 140px / control flexible) with hints under the control;
  HBox rows had let each hint steal different amounts of space and misalign the fields
- ToolCard kind label needs `minWidth = USE_PREF_SIZE` or it collapses to an ellipsis when narrow
- build-app.bat and package-windows.* now abort with a clear message if the app is still running
  (its bundled runtime DLLs lock the dist folder)
- Tip: review UI with `-Dgroups=preview` renders and measure pixels rather than eyeballing
