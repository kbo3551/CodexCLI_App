#!/usr/bin/env python3
"""Phase 0 probe #4: confirm localImage + mention user inputs are accepted as sent by the GUI."""
import json
import os
import subprocess
import threading
import time

CWD = "/tmp/probe-ws3"
CODEX = os.environ.get("CODEX_BIN", "codex")
os.makedirs(CWD, exist_ok=True)

note = os.path.join(CWD, "note.txt")
with open(note, "w", encoding="utf-8") as fh:
    fh.write("secret-token-value: BANANA42\n")

# 2x2 PNG so localImage has a real file to read.
png = os.path.join(CWD, "dot.png")
import base64
with open(png, "wb") as fh:
    fh.write(base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAEUlEQVR4nGP8z8DAwMDAwAAABQABh6FO1AAAAABJRU5ErkJggg=="))

proc = subprocess.Popen([CODEX, "app-server", "--stdio"],
                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                        text=True, encoding="utf-8", bufsize=1)
lock = threading.Lock()
results = {}
notes = []


def send(msg):
    with lock:
        proc.stdin.write(json.dumps(msg, ensure_ascii=False) + "\n")
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
            print("SERVER_REQUEST", msg["method"], flush=True)
            send({"id": msg["id"], "result": {"decision": "accept"}})
        elif "method" in msg:
            m = msg["method"]
            if m in ("item/started", "item/completed"):
                item = msg["params"]["item"]
                notes.append((m, item.get("type"), item.get("text", "")[:120]))
            elif m == "error":
                print("ERROR NOTIF", json.dumps(msg["params"])[:400], flush=True)
            elif m == "turn/completed":
                notes.append((m, msg["params"]["turn"]["status"], ""))
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
    "clientInfo": {"name": "codex-desktop", "title": "CodexDesktop", "version": "0.1.0"},
    "capabilities": {"experimentalApi": False, "requestAttestation": False}}})
print("INIT ok", flush=True)
wait(1)
send({"method": "initialized"})

send({"id": 2, "method": "thread/start", "params": {"cwd": CWD}})
started = wait(2)
tid = started["thread"]["id"]
print("thread", tid, flush=True)

# Exactly what Composer -> CodexSessionService.sendUserMessage builds.
send({"id": 3, "method": "turn/start", "params": {
    "threadId": tid,
    "input": [
        {"type": "localImage", "path": png},
        {"type": "mention", "name": "note.txt", "path": note},
        {"type": "text", "text": "Reply with only the word OK.", "text_elements": []},
    ]}})
r = wait(3, 60)
if "__error__" in r:
    print("TURN/START REJECTED:", json.dumps(r["__error__"])[:500], flush=True)
else:
    print("turn/start accepted, turn:", r["turn"]["id"], flush=True)
    end = time.time() + 150
    while time.time() < end:
        if any(n[0] == "turn/completed" for n in notes):
            break
        time.sleep(0.3)
    for kind, a, b in notes:
        print(f"{kind:16} {a:20} {b}", flush=True)

proc.stdin.close()
try:
    proc.wait(timeout=5)
except subprocess.TimeoutExpired:
    proc.terminate()
