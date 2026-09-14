# MEMORY

## [1] Phase 0 - env + protocol verified
- Scope: CodexDesktop (Java 21 + JavaFX Windows GUI driving WSL `codex app-server --stdio`)
- Change: `docs/PROTOCOL.md` written from live probes; `.protoref/` holds the probe scripts
- Facts:
  - WSL distro `Ubuntu-24.04`; codex-cli `0.154.0`; bin `/home/boryeong/.local/bin/codex`
  - Protocol = line JSON-RPC **without** `jsonrpc` field; notifications add `emittedAtMs`
  - Flow: `initialize` -> `initialized` -> `thread/start{cwd,approvalPolicy,sandbox}` -> `turn/start`
  - Stream: `turn/started`, `item/started|completed`, `item/agentMessage/delta`,
    `item/reasoning/*Delta`, `item/commandExecution/outputDelta`, `turn/diff/updated`,
    `thread/status/changed`, `turn/completed{status}`
  - Approvals = server->client requests `item/commandExecution/requestApproval`,
    `item/fileChange/requestApproval`; reply `{"decision":"accept|acceptForSession|decline|cancel"}`
  - Stop = `turn/interrupt{threadId,turnId}`; history = `thread/list{cwd}`, `thread/resume`,
    `thread/turns/list`; usage = `thread/tokenUsage/updated`, `account/rateLimits/updated`
  - Unknown server requests MUST get an error reply or the turn stalls
  - `WSL_UTF8=1` needed or wsl.exe prints UTF-16LE
- Toolchain: portable `C:\Users\USER\devtools\jdk-21.0.12.1+1` + `apache-maven-3.9.9`
  (system java is 1.8 32-bit; always pass JAVA_HOME explicitly)
- State: done
- Next: see MEMORY_02
