#!/usr/bin/env python3
"""Probe #6: fuzzyFileSearch — the backing call for @ file mentions."""
import json
import os
import subprocess
import threading
import time

CODEX = os.environ.get("CODEX_BIN", "codex")
ROOT = os.environ.get("SEARCH_ROOT", "/mnt/c/Users/USER/Desktop/dev/02.project/temp/devTool/CodexDesktop")

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

for n, query in enumerate(["Composer", "office", "pom", ""], start=2):
    send({"id": n, "method": "fuzzyFileSearch",
          "params": {"query": query, "roots": [ROOT], "cancellationToken": None}})
    started = time.time()
    r = wait(n, 60)
    elapsed = (time.time() - started) * 1000
    if "__error__" in r:
        print(f"query={query!r} ERROR:", json.dumps(r["__error__"])[:300])
        continue
    files = r.get("files", [])
    print(f"query={query!r} -> {len(files)} hits in {elapsed:.0f} ms")
    for hit in files[:4]:
        print("   root=", hit.get("root"))
        print("   path=", hit.get("path"), "| name=", hit.get("file_name"),
              "| type=", hit.get("match_type"), "| score=", hit.get("score"))

proc.stdin.close()
try:
    proc.wait(timeout=5)
except subprocess.TimeoutExpired:
    proc.terminate()
