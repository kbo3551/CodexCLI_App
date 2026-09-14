#!/usr/bin/env python3
"""Probe #5: what does model/list actually return, and does turn/start accept model+effort?"""
import json
import os
import subprocess
import threading
import time

CODEX = os.environ.get("CODEX_BIN", "codex")
CWD = "/tmp/probe-ws"
os.makedirs(CWD, exist_ok=True)

proc = subprocess.Popen([CODEX, "app-server", "--stdio"],
                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                        text=True, encoding="utf-8", bufsize=1)
lock = threading.Lock()
results = {}


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
        if "result" in msg:
            results[msg["id"]] = msg["result"]
        elif "error" in msg and "id" in msg:
            results[msg["id"]] = {"__error__": msg["error"]}


threading.Thread(target=reader, daemon=True).start()
threading.Thread(target=lambda: [None for _ in proc.stderr], daemon=True).start()


def wait(rid, timeout=60):
    end = time.time() + timeout
    while time.time() < end:
        if rid in results:
            return results[rid]
        time.sleep(0.05)
    raise TimeoutError(str(rid))


send({"id": 1, "method": "initialize", "params": {
    "clientInfo": {"name": "codex-desktop", "title": "CodexDesktop", "version": "0.1.0"},
    "capabilities": {"experimentalApi": False, "requestAttestation": False}}})
wait(1)
send({"method": "initialized"})

send({"id": 2, "method": "model/list", "params": {"limit": 40}})
models = wait(2, 60)
if "__error__" in models:
    print("model/list ERROR:", json.dumps(models["__error__"])[:400])
else:
    data = models.get("data", [])
    print("models:", len(data), "nextCursor:", models.get("nextCursor"))
    for m in data[:6]:
        print("---")
        print("  id            :", m.get("id"))
        print("  model         :", m.get("model"))
        print("  displayName   :", m.get("displayName"))
        print("  isDefault     :", m.get("isDefault"))
        print("  hidden        :", m.get("hidden"))
        print("  defaultEffort :", json.dumps(m.get("defaultReasoningEffort")))
        print("  efforts       :", json.dumps(m.get("supportedReasoningEfforts"))[:400])

# Does turn/start accept model + effort overrides? Start a thread and send a tiny turn.
send({"id": 3, "method": "thread/start", "params": {"cwd": CWD}})
started = wait(3, 60)
tid = started["thread"]["id"]
print("\nthread model:", started.get("model"), "effort:", json.dumps(started.get("reasoningEffort")))

if "__error__" not in models and models.get("data"):
    target = None
    for m in models["data"]:
        if not m.get("hidden") and not m.get("isDefault"):
            target = m
            break
    target = target or models["data"][0]
    efforts = target.get("supportedReasoningEfforts") or []
    effort = None
    if efforts:
        first = efforts[0]
        effort = first if isinstance(first, str) else (first.get("effort") or first.get("id"))
    params = {"threadId": tid,
              "input": [{"type": "text", "text": "Reply with OK only.", "text_elements": []}],
              "model": target.get("model")}
    if effort:
        params["effort"] = effort
    print("\nturn/start override ->", json.dumps({"model": params.get("model"),
                                                  "effort": params.get("effort")}))
    send({"id": 4, "method": "turn/start", "params": params})
    r = wait(4, 90)
    if "__error__" in r:
        print("turn/start REJECTED:", json.dumps(r["__error__"])[:500])
    else:
        print("turn/start accepted, turn:", r["turn"]["id"])
        send({"id": 5, "method": "turn/interrupt",
              "params": {"threadId": tid, "turnId": r["turn"]["id"]}})
        try:
            wait(5, 30)
            print("interrupted to avoid spending quota")
        except TimeoutError:
            pass

proc.stdin.close()
try:
    proc.wait(timeout=5)
except subprocess.TimeoutExpired:
    proc.terminate()
