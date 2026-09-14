# CodexDesktop

A JavaFX desktop front-end for the Codex CLI that is already installed in WSL.

It does not talk to any OpenAI API. It launches `codex app-server --stdio` inside your WSL
distribution over `wsl.exe` and speaks the app-server protocol to it, so your existing
`codex login`, `~/.codex/config.toml`, sandbox policy and approval policy are used unchanged.

```
Windows 11
└─ CodexDesktop.exe            Java 21 + JavaFX, no Electron, no WebView
      │  stdin/stdout, line-delimited JSON-RPC
      ▼
   wsl.exe -d <distro> --cd <project> -- codex app-server --stdio
      └─ Ubuntu: existing Codex login, config, sandbox, bash, git
```

## Requirements

- Windows 10/11 with WSL 2 and a distribution that has Codex installed and logged in
- JDK 21 and Maven 3.9+ (only to build; the packaged app bundles its own runtime)

Verified against `codex-cli 0.154.0` on `Ubuntu-24.04`.

## Run from source

```bash
mvn clean javafx:run
```

## Build a Windows app with a bundled runtime

Easiest: double-click **`build-app.bat`** in the repository root. It locates a JDK 21 and Maven on
its own (including a portable copy under `%USERPROFILE%\devtools\`), builds, and opens the output
folder. `build-app.bat installer` builds the installer instead.

Equivalent from a shell:

```bash
scripts/package-windows.sh              # dist/CodexDesktop/CodexDesktop.exe
scripts/package-windows.sh installer    # dist/CodexDesktop-0.1.0.exe  (needs WiX Toolset)
```

`scripts\package-windows.cmd` is the same thing for cmd.exe. The app-image target needs nothing
beyond a JDK 21; the installer target needs WiX on `PATH`.

To move the app, copy the whole `dist\CodexDesktop` folder — the bundled Java runtime sits next to
the exe, so the exe on its own will not start.

## Tests

```bash
mvn test                                                        # unit tests
mvn test -Dgroups=integration -Dsurefire.excludedGroups=none    # real Codex, real turn, uses quota
mvn test -Dgroups=preview     -Dsurefire.excludedGroups=none    # renders the UI to target/ui-preview
```

The integration test drives the production classes against the installed Codex: it starts the
app-server, opens a thread, runs a turn that edits a file, answers the resulting approval, checks
the diff, then interrupts a second turn and asserts the process is gone.

## What it does

| Area | Notes |
|---|---|
| Projects | Pick a Windows folder; the WSL path is derived and used as the thread `cwd` |
| Threads | New / list / resume per project via `thread/start`, `thread/list`, `thread/resume` |
| Chat | Streaming assistant text, Markdown rendering (commonmark, no WebView) |
| Attachments | Files and images by button, drag-and-drop or Ctrl+V; sent as `mention` / `localImage` |
| `@` mentions | Type `@` to fuzzy-search the project (`fuzzyFileSearch`) and insert a file reference |
| `/` commands | Command palette for new / model / compact / diff / status / office / settings / stop |
| Tool calls | Compact cards for read / search / command / file edit, expanded on demand |
| Approvals | Inline Allow / Allow for session / Deny wired to the real approval requests |
| Changes | `turn/diff/updated` drives a per-file diff panel; git branch and counts in the status bar |
| Usage | Context-window share and account rate limits, like the CLI status view |
| Office | Pixel-art view where each thread is an agent at a desk; it walks over and types while a turn runs |
| Resizable | Every panel boundary is a draggable divider: sidebar, conversation, office and changes |
| Model | Model and reasoning-effort picker from `model/list`; context compaction |
| Settings | WSL executable, distribution, Codex path and arguments, policies, theme, language, text size |
| Diagnostics | Runs the same commands the app uses and shows each one next to its result |

Interface language: Korean and English (`설정 → 언어`). The bundled D2Coding font is used for the
whole interface.

## Agent office

The **사무실 / Office** button in the top bar opens a top-down pixel office **beside** the
conversation, so the chat stays usable and the same button (or the panel's ×) closes it again. Every
thread in the project owns a desk and is numbered `Agent 1..N`; hovering one shows its thread title,
clicking one opens that thread. The thread you have open is ringed and labelled. It is driven by the
same protocol events as the rest of the UI:

| Event | Office |
|---|---|
| `turn/started` | the agent walks to its desk |
| activity change | it sits and types, with the current activity in a speech bubble |
| approval request | a marker appears above its head until you answer |
| `turn/completed` | it stops typing and the bubble shows done / stopped / failed |

Agents with nothing to do wander the room and return to their seats; a starting turn always wins,
so a busy agent is the one sitting and typing. Faces show what they are doing — narrowed eyes while
working, wide eyes and a raised hand while waiting on you, a small smile when idle — and each agent
keeps the same hair, shirt and glasses so it stays recognisable.

The room has eight desks with laptops, a meeting table in the middle that seats four more agents
once the desks fill up, plus a whiteboard, water cooler, shelves, plants and a clock.

The panel header has three actions:

| Button | Effect |
|---|---|
| `↗` | opens the office in its own window (resizable, second monitor friendly); closing it docks it back |
| `⤢` | fills the main window with the office, hiding the chat; press again to go back |
| `×` | closes the office |

Twelve desks are available; if the project has more threads the footer reports how many are not
shown. The animation loop runs only while the office is on screen **and** something is actually
moving, so an idle office that nobody is looking at costs no frames. All art is generated in
code — there are no sprite assets to ship.

## Where things live

```
src/main/java/com/codexdesktop/
├─ codex/      process host, JSON-RPC client, session orchestration, approvals
├─ model/      settings, projects, threads, usage, attachments  (records)
├─ service/    WSL command runner, path conversion, git, diagnostics, persistence
├─ ui/         shell, sidebar, composer, status bar, settings, conversation widgets
└─ i18n/       message bundle access
```

Settings, projects, logs and pasted-image scratch files live in
`%LOCALAPPDATA%\CodexDesktop`. Prompts, model output and credentials are never logged; raw
protocol JSON is logged only while Debug Logging is on.

## Protocol

`docs/PROTOCOL.md` documents the app-server protocol as observed on this machine, including the
handshake, the event stream, approvals, interrupts and history. It can be regenerated at any time:

```bash
codex app-server generate-json-schema --experimental --out DIR
codex app-server generate-ts --experimental --out DIR
```
