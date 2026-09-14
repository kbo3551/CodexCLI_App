# Codex app-server protocol notes (verified)

Environment observed on this machine (2026-09-10):

| Item | Value |
|---|---|
| WSL distro | `Ubuntu-24.04` (default, WSL 2) |
| Codex version | `codex-cli 0.154.0` |
| `codex` on PATH | `/home/boryeong/.local/bin/codex` |
| Real binary | `/home/boryeong/.codex/packages/standalone/releases/0.154.0-x86_64-unknown-linux-musl/bin/codex` |
| CODEX_HOME | `/home/boryeong/.codex` |
| Auth | `getAuthStatus` -> `{"authMethod":"chatgpt","requiresOpenaiAuth":true}` |
| Transport | `codex app-server --stdio` (also `--listen unix://`, `ws://`) |

Everything below was captured from live runs against that install
(`.protoref/probe.sh`, `probe2.py`, `probe3.py`), not inferred.

Authoritative schema can be regenerated at any time:

```bash
codex app-server generate-json-schema --experimental --out DIR
codex app-server generate-ts        --experimental --out DIR
```

## Framing

Line-delimited JSON (one object per line) on stdin/stdout. **No `jsonrpc`
field** — `JSONRPCRequest` requires only `id` + `method`, notifications only
`method`, responses only `id` + `result`. `id` may be string or int64.
Server notifications carry an extra `emittedAtMs` field next to `method`/`params`.
stderr carries human-readable tracing logs (ANSI colored), not protocol data.
The server exits on stdin EOF.

## Handshake

```
-> {"id":1,"method":"initialize","params":{"clientInfo":{"name":..,"title":..,"version":..},
                                           "capabilities":{"experimentalApi":false,"requestAttestation":false}}}
<- {"id":1,"result":{"userAgent":"...","codexHome":"/home/…/.codex","platformFamily":"unix","platformOs":"linux"}}
-> {"method":"initialized"}
```

`clientInfo.name` becomes the thread `originator`.

## Thread + turn lifecycle

```
-> {"id":2,"method":"thread/start","params":{"cwd":"/abs/path","approvalPolicy":"on-request","sandbox":"workspace-write"}}
<- {"id":2,"result":{"thread":{"id":"01a0…","cwd":…,"status":{"type":"idle"},…},"model":"gpt-6-astra",
                     "approvalPolicy":"on-request","sandbox":{…},"reasoningEffort":"medium"}}
<- {"method":"thread/started","params":{"thread":{…}}}

-> {"id":3,"method":"turn/start","params":{"threadId":"01a0…",
      "input":[{"type":"text","text":"…","text_elements":[]}]}}
<- {"id":3,"result":{"turn":{"id":"01a0…","status":"inProgress",…}}}
```

`sandbox` accepts `read-only | workspace-write | danger-full-access`;
`approvalPolicy` accepts `untrusted | on-request | never | {granular:{…}}`.
Omitting both inherits `~/.codex/config.toml`.

Observed notification order for one turn:

```
thread/status/changed   {"type":"active","activeFlags":[]}
turn/started            params.turn.id
item/started            item.type=userMessage
item/completed          item.type=userMessage
item/started            item.type=agentMessage   (phase="commentary")
item/agentMessage/delta params.delta  (per token, params.itemId)
item/completed          item.type=agentMessage
item/started/completed  item.type=reasoning | commandExecution | fileChange | …
turn/diff/updated       params.diff  (full unified git diff for the turn)
thread/tokenUsage/updated / account/rateLimits/updated
thread/status/changed   {"type":"idle"}
turn/completed          params.turn.status = completed | interrupted | failed
```

Also seen: `remoteControl/status/changed`, `mcpServer/startupStatus/updated`,
`serverRequest/resolved`, `error` (`{error:TurnError, willRetry, threadId, turnId}`).

### Item shapes actually used by the UI

- `userMessage`: `content: UserInput[]` (`{"type":"text","text":…,"text_elements":[]}`).
- `agentMessage`: `text`, `phase` = `commentary | final_answer | null`.
- `reasoning`: `summary: string[]`, `content: string[]`;
  streamed via `item/reasoning/summaryTextDelta` / `item/reasoning/textDelta`.
- `commandExecution`: `command` (e.g. `/bin/bash -lc 'ls -1'`), `cwd`, `processId`,
  `source`, `status` = `inProgress|completed|failed|declined`,
  `commandActions[]` = `read{name,path} | listFiles{path} | search{query,path} | unknown`,
  `aggregatedOutput`, `exitCode`, `durationMs`.
  Live output arrives on `item/commandExecution/outputDelta`.
- `fileChange`: `changes[] = {path, kind:{type:add|delete|update, move_path}, diff}`,
  `status` (`PatchApplyStatus`). Updates via `item/fileChange/patchUpdated`.
- Also: `mcpToolCall`, `webSearch`, `plan`, `contextCompaction`, `imageView`, …

## Approvals (server -> client requests)

Server sends a request; the client answers with a normal result using the same `id`.

| Method | Response |
|---|---|
| `item/commandExecution/requestApproval` | `{"decision":"accept"｜"acceptForSession"｜"decline"｜"cancel"}` |
| `item/fileChange/requestApproval` | same four decisions |
| `item/tool/requestUserInput` | text answers |
| `item/permissions/requestApproval` | requires a `GrantedPermissionProfile` — not implemented, answered with a JSON-RPC error |
| `mcpServer/elicitation/request`, `item/tool/call`, `attestation/generate`, `account/chatgptAuthTokens/refresh` | not implemented, answered with a JSON-RPC error |
| `currentTime/read` | `{"currentTimeAt": <unix seconds>}` |

`CommandExecutionRequestApprovalParams` carries `threadId/turnId/itemId`,
`kind` (`command|writeStdin`), `command`, `cwd`, `commandActions`, `reason`,
optional `approvalId` (must be echoed back? no — the response is matched by
JSON-RPC `id`), and optional `availableDecisions` the client should prefer.
Verified live: a `fileChange` under a `read-only` sandbox produced
`item/fileChange/requestApproval`, and `{"decision":"accept"}` let it proceed.

Unanswered server requests stall the turn, so unknown methods must be answered
with an error rather than ignored.

## Interrupt

```
-> {"id":5,"method":"turn/interrupt","params":{"threadId":…,"turnId":…}}
<- {"id":5,"result":{}}
<- {"method":"turn/completed","params":{"turn":{"status":"interrupted",…}}}
```

Verified while a `sleep 40` command was running. No process kill needed.

## History

- `thread/list` -> `{data: Thread[], nextCursor, backwardsCursor}`; filters that
  matter here: `cwd` (string or array), `limit`, `sortKey`, `sortDirection`,
  `archived`. Each `Thread` has `id`, `preview`, `name`, `cwd`, `updatedAt`,
  `gitInfo`, `status`.
- `thread/resume` -> `{threadId, excludeTurns?, initialTurnsPage?}`; returns the
  `Thread` with `turns` populated unless `excludeTurns` is set.
- `thread/turns/list` -> `{data: Turn[], nextCursor, backwardsCursor}`,
  descending by default; `itemsView` controls item detail.
- `thread/unsubscribe` releases a thread without killing the server.

## Notes for the client implementation

1. `wsl.exe` writes its own messages as UTF-16LE unless `WSL_UTF8=1` is set in
   the environment — required for readable diagnostics.
2. Codex resolves `cwd` inside WSL, so Windows paths must be converted
   (`C:\x\y` -> `/mnt/c/x/y`).
3. `agentMessage` items with `phase="commentary"` are progress narration;
   `final_answer` is the answer. Both should render, weighted differently.
4. `turn/diff/updated` repeats the full turn diff, so the view can simply
   replace its content.
