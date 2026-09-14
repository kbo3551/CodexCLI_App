#!/usr/bin/env bash
# Phase 0 probe: verify codex app-server stdio protocol on the local install.
# Sends a real handshake and a real turn, captures raw JSONL for inspection.
set -uo pipefail

WS=/tmp/probe-ws
OUT=/tmp/appserver.out
ERR=/tmp/appserver.err

mkdir -p "$WS"
printf 'hello world\n' > "$WS/README.md"
: > "$OUT"
: > "$ERR"

{
  printf '%s\n' '{"id":1,"method":"initialize","params":{"clientInfo":{"name":"codex-desktop-probe","title":"probe","version":"0.1.0"},"capabilities":{"experimentalApi":false,"requestAttestation":false}}}'
  sleep 2
  printf '%s\n' '{"method":"initialized"}'
  sleep 1
  printf '%s\n' '{"id":2,"method":"getAuthStatus","params":{}}'
  sleep 1
  printf '%s\n' '{"id":3,"method":"thread/start","params":{"cwd":"/tmp/probe-ws","approvalPolicy":"on-request","sandbox":"read-only"}}'
  sleep 5
} | codex app-server --stdio > "$OUT" 2> "$ERR"

echo "=== STDOUT (methods only) ==="
python3 - "$OUT" <<'PY'
import json, sys
path = sys.argv[1]
with open(path, encoding='utf-8', errors='replace') as fh:
    for line in fh:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except Exception as exc:
            print("PARSE_FAIL", exc, line[:200])
            continue
        if 'method' in msg and 'id' in msg:
            print("SERVER_REQ ", msg['method'], "id=", msg['id'])
        elif 'method' in msg:
            print("NOTIF      ", msg['method'])
        elif 'result' in msg:
            print("RESULT     id=", msg['id'], json.dumps(msg['result'])[:400])
        elif 'error' in msg:
            print("ERROR      id=", msg.get('id'), json.dumps(msg['error'])[:400])
        else:
            print("OTHER      ", line[:200])
PY

echo "=== STDERR (tail) ==="
tail -20 "$ERR"
