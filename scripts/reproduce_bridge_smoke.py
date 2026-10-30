#!/usr/bin/env python3
"""Exercise the compiled QQ Bridge health and authentication boundary locally."""
import argparse
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0)); port = sock.getsockname()[1]
    token = secrets.token_hex(32)
    process = subprocess.Popen(["node", "dist/index.js"], cwd=ROOT / "cloud-java/qq-bridge",
                               env={**os.environ, "PORT": str(port), "QQ_BRIDGE_SHARED_TOKEN": token},
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    def request(path, bearer=None):
        headers = {"Authorization": "Bearer " + bearer} if bearer else {}
        try:
            with urlopen(Request(f"http://127.0.0.1:{port}" + path, headers=headers), timeout=2) as reply:
                return reply.status, json.loads(reply.read())
        except HTTPError as exc: return exc.code, json.loads(exc.read())
    try:
        for _ in range(50):
            if process.poll() is not None: raise RuntimeError("QQ Bridge exited before readiness")
            try:
                status, body = request("/health", token)
                if status == 200 and body.get("status") == "UP": break
            except (URLError, ConnectionError): pass
            time.sleep(.1)
        else: raise RuntimeError("QQ Bridge readiness timeout")
        checks = []
        for label, path, bearer, expected in [("authenticated_health", "/health", token, 200),
                                            ("missing_token_denied", "/v1/instances/example", None, 401),
                                            ("invalid_token_denied", "/health", "invalid-test-token", 401)]:
            status, body = request(path, bearer)
            if status != expected: raise AssertionError(f"{label}: HTTP {status}, expected {expected}")
            if expected == 401 and body.get("code") != "UNAUTHORIZED": raise AssertionError("Unexpected auth response")
            checks.append({"name": label, "expectedHttpStatus": expected, "actualHttpStatus": status, "status": "PASS"})
        report = {"status": "PASS", "checks": checks,
                  "scope": "Compiled bridge process, authenticated readiness and unauthorized request rejection",
                  "containerCreated": False, "externalAccountLogin": False}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report))
    finally:
        process.terminate()
        try: process.wait(timeout=5)
        except subprocess.TimeoutExpired: process.kill(); process.wait()

if __name__ == "__main__": main()
