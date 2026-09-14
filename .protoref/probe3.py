#!/usr/bin/env python3
"""Phase 0 probe #3: approval flow, fileChange items, turn diff, interrupt, thread/list."""
import json
import os
import subprocess
import threading
import time

CWD = "/tmp/probe-ws2"
CODEX = os.environ.get("CODEX_BIN", "codex")
os.makedirs(CWD, exist_ok=True)
with open(os.path.join(CWD, "a.txt"), "w", encoding="utf-8") as fh:
    fh.write("line1\n")

proc = subprocess.Popen(
    [CODEX, "app-server", "--stdio"],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    text=True, encoding="utf-8", bufsize=1,
)
lock = threading.Lock()
results = {}
events = []
interesting = {
    "item/started", "item/completed", "turn/diff/updated", "turn/completed",
    "thread/status/changed", "error", "item/reasoning/summaryTextDelta",
    "item/commandExecution/outputDelta", "item/fileChange/patchUpdated",
    "serverRequest/resolved",
}


def send(msg):
    line = json.dumps(msg, ensure_ascii=False)
    with lock:
        proc.stdin.write(line + "\n")
        proc.stdin.flush()


def reader():
    for line in proc.stdout:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except Exception:
            continue
        if "method" in msg and "id" in msg:
            events.append(("REQ", msg["method"], msg))
            print("<<< SERVER_REQUEST", msg["method"], json.dumps(msg.get("params"))[:900], flush=True)
        elif "method" in msg:
            if msg["method"] in interesting:
                events.append(("NOTIF", msg["method"], msg))
        elif "result" in msg:
            results[msg["id"]] = msg["result"]
        elif "error" in msg:
            results[msg["id"]] = {"__error__": msg["error"]}


threading.Thread(target=reader, daemon=True).start()
threading.Thread(target=lambda: [print("!!!", l.rstrip()[:200], flush=True) for l in proc.stderr], daemon=True).start()


def wait(rid, timeout=90):
    end = time.time() + timeout
    while time.time() < end:
        if rid in results:
            return results[rid]
        time.sleep(0.05)
    raise TimeoutError(str(rid))


send({"id": 1, "method": "initialize", "params": {
    "clientInfo": {"name": "codex-desktop-probe", "title": "probe", "version": "0.1.0"},
    "capabilities": {"experimentalApi": False, "requestAttestation": False}}})
wait(1)
send({"method": "initialized"})

send({"id": 2, "method": "thread/start", "params": {
    "cwd": CWD, "approvalPolicy": "on-request", "sandbox": "read-only"}})
tid = wait(2)["thread"]["id"]
print("THREAD", tid, flush=True)

# --- Turn A: force a file change under a read-only sandbox -> approval expected
send({"id": 3, "method": "turn/start", "params": {
    "threadId": tid,
    "input": [{"type": "text", "text": "Use apply_patch to append the line 'line2' to a.txt. Do not use shell. Reply 'done'.", "text_elements": []}]}})

approved = []
end = time.time() + 180
while time.time() < end:
    for kind, method, msg in list(events):
        if kind == "REQ" and method.endswith("requestApproval"):
            print("APPROVING", method, flush=True)
            send({"id": msg["id"], "result": {"decision": "accept"}})
            approved.append(method)
            events.remove((kind, method, msg))
    if any(k == "NOTIF" and m == "turn/completed" for k, m, _ in events):
        break
    time.sleep(0.2)

print("\n=== TURN A EVENTS ===", flush=True)
for kind, method, msg in events:
    p = msg.get("params", {})
    extra = ""
    if method in ("item/started", "item/completed"):
        extra = "type=" + str(p.get("item", {}).get("type")) + " status=" + str(p.get("item", {}).get("status"))
    elif method == "turn/diff/updated":
        extra = "diff=" + repr(p.get("diff", ""))[:220]
    print(f"{kind:6} {method:42} {extra}", flush=True)

print("\n=== fileChange item payload ===", flush=True)
for kind, method, msg in events:
    if method == "item/completed" and msg["params"]["item"].get("type") == "fileChange":
        print(json.dumps(msg, ensure_ascii=False)[:1600], flush=True)
print("approvals seen:", approved, flush=True)

# --- Turn B: interrupt
events.clear()
send({"id": 4, "method": "turn/start", "params": {
    "threadId": tid,
    "input": [{"type": "text", "text": "Run `sleep 40` with the shell tool.", "text_elements": []}]}})
turn_id = None
end = time.time() + 60
while time.time() < end and turn_id is None:
    r = results.get(4)
    if r:
        turn_id = r.get("turn", {}).get("id")
        break
    time.sleep(0.2)
print("\nTURN B id:", turn_id, flush=True)
time.sleep(12)
if turn_id:
    send({"id": 5, "method": "turn/interrupt", "params": {"threadId": tid, "turnId": turn_id}})
    try:
        print("INTERRUPT RESULT:", json.dumps(wait(5, 40))[:400], flush=True)
    except TimeoutError:
        print("INTERRUPT: no result within 40s", flush=True)
time.sleep(4)
print("=== TURN B EVENTS ===", flush=True)
for kind, method, msg in events:
    p = msg.get("params", {})
    extra = ""
    if method in ("item/started", "item/completed"):
        extra = "type=" + str(p.get("item", {}).get("type")) + " status=" + str(p.get("item", {}).get("status"))
    elif method == "turn/completed":
        extra = "status=" + str(p.get("turn", {}).get("status")) + " err=" + json.dumps(p.get("turn", {}).get("error"))[:160]
    elif method == "thread/status/changed":
        extra = json.dumps(p.get("status"))
    print(f"{kind:6} {method:42} {extra}", flush=True)

# --- thread/list (no model call)
send({"id": 6, "method": "thread/list", "params": {"limit": 3, "cwd": CWD}})
lst = wait(6, 60)
print("\n=== thread/list ===", flush=True)
print("count:", len(lst.get("data", [])), "nextCursor:", lst.get("nextCursor"), flush=True)
for t in lst.get("data", [])[:3]:
    print("  ", t["id"], "| name=", t.get("name"), "| preview=", (t.get("preview") or "")[:50], "| cwd=", t.get("cwd"), "| updatedAt=", t.get("updatedAt"), flush=True)

# --- thread/turns/list on the current thread
send({"id": 7, "method": "thread/turns/list", "params": {"threadId": tid, "limit": 5}})
try:
    turns = wait(7, 60)
    print("\n=== thread/turns/list keys ===", list(turns.keys()), flush=True)
    print(json.dumps(turns)[:900], flush=True)
except TimeoutError:
    print("thread/turns/list timeout", flush=True)

proc.stdin.close()
try:
    proc.wait(timeout=5)
except subprocess.TimeoutExpired:
    proc.terminate()
