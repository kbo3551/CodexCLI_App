#!/usr/bin/env python3
"""Phase 0 probe #2: drive a real turn through codex app-server --stdio.

Records the exact event ordering and payload shapes so the Java client can be
implemented against observed behavior instead of guesses.
"""
import json
import os
import subprocess
import sys
import threading
import time

CWD = "/tmp/probe-ws"
RAW = "/tmp/appserver2.jsonl"
CODEX = os.environ.get("CODEX_BIN", "codex")

proc = subprocess.Popen(
    [CODEX, "app-server", "--stdio"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    encoding="utf-8",
    bufsize=1,
)

raw = open(RAW, "w", encoding="utf-8")
lock = threading.Lock()
results = {}
events = []
done = threading.Event()


def send(msg):
    line = json.dumps(msg, ensure_ascii=False)
    with lock:
        proc.stdin.write(line + "\n")
        proc.stdin.flush()
    print(">>> " + line[:160], flush=True)


def reader():
    for line in proc.stdout:
        line = line.strip()
        if not line:
            continue
        raw.write(line + "\n")
        raw.flush()
        try:
            msg = json.loads(line)
        except Exception as exc:  # noqa: BLE001
            print("PARSE_FAIL", exc, line[:200], flush=True)
            continue
        if "method" in msg and "id" in msg:
            events.append(("server_request", msg["method"], msg))
            print("<<< SERVER_REQUEST", msg["method"], json.dumps(msg.get("params"))[:600], flush=True)
        elif "method" in msg:
            events.append(("notification", msg["method"], msg))
        elif "result" in msg:
            results[msg["id"]] = msg["result"]
        elif "error" in msg:
            results[msg["id"]] = {"__error__": msg["error"]}
            print("<<< ERROR", json.dumps(msg["error"])[:400], flush=True)


def errreader():
    for line in proc.stderr:
        print("!!! stderr:", line.rstrip()[:300], flush=True)


threading.Thread(target=reader, daemon=True).start()
threading.Thread(target=errreader, daemon=True).start()


def wait_for(rid, timeout=60):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if rid in results:
            return results[rid]
        time.sleep(0.05)
    raise TimeoutError(f"no result for id={rid}")


send({
    "id": 1,
    "method": "initialize",
    "params": {
        "clientInfo": {"name": "codex-desktop-probe", "title": "probe", "version": "0.1.0"},
        "capabilities": {"experimentalApi": False, "requestAttestation": False},
    },
})
init = wait_for(1)
print("INIT:", json.dumps(init), flush=True)
send({"method": "initialized"})

send({
    "id": 2,
    "method": "thread/start",
    "params": {"cwd": CWD, "approvalPolicy": "on-request", "sandbox": "workspace-write"},
})
started = wait_for(2, 60)
thread_id = started["thread"]["id"]
print("THREAD:", thread_id, "model=", started.get("model"), "approval=", started.get("approvalPolicy"), flush=True)

send({
    "id": 3,
    "method": "turn/start",
    "params": {
        "threadId": thread_id,
        "input": [{"type": "text", "text": "Run `ls -1` in the workspace, then append a line 'probe ok' to README.md. Reply with one short sentence when done.", "text_elements": []}],
    },
})

# Wait for turn/completed or an error notification.
deadline = time.time() + 180
turn_done = False
while time.time() < deadline and not turn_done:
    for kind, method, msg in list(events):
        if kind == "notification" and method in ("turn/completed", "turn/failed"):
            turn_done = True
        if kind == "server_request" and method in (
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
        ):
            rid = msg["id"]
            print("AUTO-APPROVING", method, "id=", rid, flush=True)
            send({"id": rid, "result": {"decision": "accept"}})
            events.remove((kind, method, msg))
    time.sleep(0.2)

time.sleep(1.0)
print("\n=== EVENT ORDER ===", flush=True)
for kind, method, msg in events:
    extra = ""
    if method in ("item/started", "item/completed"):
        item = msg.get("params", {}).get("item", {})
        extra = f"item.type={item.get('type')} id={item.get('id')}"
    elif method == "item/agentMessage/delta":
        extra = "delta=" + repr(msg.get("params", {}).get("delta", ""))[:40]
    elif method == "thread/status/changed":
        extra = json.dumps(msg.get("params", {}).get("status"))
    elif method == "turn/diff/updated":
        extra = "difflen=" + str(len(msg.get("params", {}).get("diff", "")))
    print(f"{kind:16} {method:45} {extra}", flush=True)

print("\n=== SAMPLE PAYLOADS ===", flush=True)
seen = set()
for kind, method, msg in events:
    key = method
    if method in ("item/started", "item/completed"):
        key = method + ":" + str(msg.get("params", {}).get("item", {}).get("type"))
    if key in seen:
        continue
    seen.add(key)
    print("---", key, flush=True)
    print(json.dumps(msg, ensure_ascii=False)[:1500], flush=True)

send({"id": 99, "method": "thread/unsubscribe", "params": {"threadId": thread_id}})
time.sleep(0.5)
proc.stdin.close()
try:
    proc.wait(timeout=5)
except subprocess.TimeoutExpired:
    proc.terminate()
raw.close()
print("\nRAW ->", RAW, flush=True)
