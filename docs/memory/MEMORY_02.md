# MEMORY

## [2] Phase 1-9 done - builds, packages, verified against real Codex
- Scope: `CodexDesktop/` Maven module (Java 21, JavaFX 21.0.12, Jackson, commonmark, Logback, JUnit 5)
- Layout: `codex/` (WslCodexHost, CodexAppServerClient, CodexSessionService, UiEventPump),
  `model/` records, `service/` (WslCommandRunner, WindowsWslPathConverter, Git, Diagnostics,
  Settings, Project, Attachment), `ui/` (MainWindow, Sidebar, Composer, StatusBar, SettingsView,
  ChangesPanel, UsagePopup, conversation/*, markdown/*), `i18n/`
- Features: streaming chat, tool cards, inline approvals, diff panel, thread list/resume,
  file+image attachments (`localImage`/`mention`), usage panel (context % + rate limits),
  model/effort picker (`model/list`), compaction, settings + diagnostics, ko/en UI, bundled D2Coding
- Verified: `mvn test` 23 pass; integration test against real Codex covers connect -> thread/start
  -> streaming -> fileChange approval accept -> real file edited -> turn diff -> usage -> interrupt
  -> process gone; packaged `dist/CodexDesktop/CodexDesktop.exe` (93MB, bundled runtime) starts,
  connects, exits with code=0 and leaves no orphan app-server
- Toolchain: always pass `JAVA_HOME=C:\Users\USER\devtools\jdk-21.0.12.1+1` and use
  `C:\Users\USER\devtools\apache-maven-3.9.9\bin\mvn.cmd` (system java is 1.8 32-bit)
- Test tags: `integration` and `preview` excluded by default via `surefire.excludedGroups`;
  run with `-Dgroups=X -Dsurefire.excludedGroups=none`. `preview` renders real widgets to
  `target/ui-preview/*.png` headlessly - use it for UI review instead of launching a window
- Gotchas fixed (do not regress):
  - `ResourceBundle.getBundle` falls back to the default locale, so English needs `Locale.ROOT`
    plus `getNoFallbackControl`
  - JavaFX default `.label` colour is near-black; base.css sets `.label { -fx-text-fill: -c-text; }`
  - D2Coding has no emoji glyphs - use text marks ("@", "IMG"); the ttc needs `Font.loadFonts`
  - `\u` inside a Java comment is still decoded, so avoid Windows paths in javadoc
  - Connection state is assigned synchronously; only listener callbacks go through runLater
- Next: verify the WiX installer target; check long-conversation performance (node cap is 400)
