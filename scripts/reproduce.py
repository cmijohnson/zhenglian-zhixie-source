#!/usr/bin/env python3
"""Run deterministic fixtures and application verification; emit auditable results.

Modes: data-only requires only Python; offline uses installed npm dependencies and
Maven cache with selected backend unit tests; full installs locked npm dependencies
and runs mvn verify, including database/authorization integration tests via Docker.
External AI providers and authenticated QQ/Feishu/WeCom accounts are not invoked.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SEED = 20260927
OFFLINE_TESTS = ",".join(["QqDataCipherTest", "QqWorkbenchRoutePolicyTest", "QqBridgeClientContractTest",
                          "QqControllerTest", "QqIntegrationServiceTest", "McpSessionRegistryTest", "McpToolRegistryTest"])

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def run(args):
    output = args.output.resolve()
    if output.exists() and any(output.iterdir()):
        raise SystemExit("Output directory must be empty; choose a new directory to preserve earlier evidence.")
    output.mkdir(parents=True, exist_ok=True)
    steps = []
    def source_state():
        try:
            revision = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True)
            listed = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True).stdout.split(b"\0")
            commit = revision.stdout.strip() or None
        except OSError:
            listed, commit = [], None
        listed += [b"scripts/reproduce.py", b"scripts/reproduce_qce_patch.py", b"scripts/reproduce_bridge_smoke.py", b"tests/competition/generate_synthetic_dataset.py", b"tests/competition/test_synthetic_dataset.py"]
        digest = hashlib.sha256()
        for raw in sorted(set(x for x in listed if x)):
            path = ROOT / raw.decode()
            if path.suffix.lower() in {".md", ".pdf", ".docx"}: continue
            digest.update(raw + b"\0")
            digest.update(path.read_bytes() if path.is_file() else b"MISSING")
        return commit, digest.hexdigest()
    start_commit, start_digest = source_state()
    versions = {}
    for tool, argv in {"java": ["java", "-version"], "node": ["node", "--version"],
                       "npm": ["npm", "--version"], "maven": ["mvn", "--version"],
                       "docker": ["docker", "--version"]}.items():
        try:
            result = subprocess.run(argv, capture_output=True, text=True, timeout=15)
            text = re.sub(r"\x1b\[[0-9;]*m", "", result.stdout + result.stderr).strip()
            versions[tool] = text.splitlines()[0] if text else "unavailable"
        except (OSError, subprocess.TimeoutExpired): versions[tool] = "unavailable"
    runtime_env = {**os.environ, "NO_COLOR": "1", "CI": "true"}
    if args.mode == "full" and not runtime_env.get("DOCKER_HOST"):
        context = subprocess.run(["docker", "context", "inspect", "--format", "{{.Endpoints.docker.Host}}"], capture_output=True, text=True)
        if context.returncode == 0 and context.stdout.strip():
            runtime_env["DOCKER_HOST"] = context.stdout.strip()
            if context.stdout.strip().startswith("unix://"):
                runtime_env.setdefault("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    def command(name, argv, cwd=ROOT):
        start = time.monotonic()
        try:
            proc = subprocess.run(argv, cwd=cwd, capture_output=True, text=True, timeout=args.timeout,
                                  env=runtime_env)
            code, content = proc.returncode, proc.stdout + proc.stderr
        except (OSError, subprocess.TimeoutExpired) as exc:
            code, content = 124 if isinstance(exc, subprocess.TimeoutExpired) else 127, str(exc)
        # Exported evidence contains relative project paths, not local account names.
        content = content.replace(str(ROOT), "<repository>").replace(str(output), "<output>")
        content = re.sub(r"/(Users|home)/[^/\s]+", "/local-user", content)
        log = output / f"{name}.log"; log.write_text(content)
        item = {"name": name, "command": argv, "workingDirectory": str(cwd.relative_to(ROOT)) or ".",
                "exitCode": code, "seconds": round(time.monotonic() - start, 3), "log": log.name,
                "logSha256": sha(log), "status": "PASS" if code == 0 else "FAIL"}
        item["command"] = [s.replace(str(ROOT), "<repository>").replace(str(output), "<output>") for s in argv]
        steps.append(item); print(f"{name}: {item['status']} ({item['seconds']}s)", flush=True)
        return code, content
    generator = ROOT / "tests/competition/generate_synthetic_dataset.py"
    data_ok = True
    for n in (1, 2):
        code, _ = command(f"generate-{n}", [sys.executable, str(generator), "--seed", str(SEED), "--output", str(output / f"dataset-{n}")])
        data_ok &= code == 0
    code, _ = command("fixture-tests", [sys.executable, "-m", "unittest", "discover", "-s", "tests/competition", "-p", "test_*.py", "-v"])
    data_ok &= code == 0
    if data_ok:
        names = ["dataset.json", "dataset-card.json", "SHA256SUMS"]
        same = all((output / "dataset-1" / name).read_bytes() == (output / "dataset-2" / name).read_bytes() for name in names)
        steps.append({"name": "byte-identical-repeat", "status": "PASS" if same else "FAIL", "exitCode": 0 if same else 1,
                      "files": {name: sha(output / "dataset-1" / name) for name in names}})
    junit = None
    if args.mode != "data-only":
        if args.mode == "full":
            command("docker-runtime", ["docker", "info", "--format", "{{.ServerVersion}}"])
            command("qce-pinned-patch", [sys.executable, "scripts/reproduce_qce_patch.py", "--output", str(output / "qce-patch.json")])
        for module in ("frontend", "qq-bridge"):
            cwd = ROOT / "cloud-java" / module
            if args.mode == "full":
                command(f"{module}-install", ["npm", "ci", "--ignore-scripts", "--no-audit", "--no-fund"], cwd)
            elif not (cwd / "node_modules").is_dir():
                steps.append({"name": f"{module}-dependencies", "exitCode": 1, "status": "FAIL", "reason": "Install locked dependencies before offline mode."})
            command(f"{module}-build", ["npm", "run", "build"], cwd)
            command(f"{module}-tests", ["npm", "run", "test:unit" if module == "frontend" else "test"], cwd)
        command("qq-bridge-runtime", [sys.executable, "scripts/reproduce_bridge_smoke.py", "--output", str(output / "qq-bridge-smoke.json")])
        test_start = time.time()
        mvn = ["mvn", "-B"]
        if args.mode == "offline": mvn += ["-o", f"-Dtest={OFFLINE_TESTS}"]
        mvn += ["verify"]
        command("backend-verify", mvn, ROOT / "cloud-java/backend")
        junit = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "reportCount": 0}
        for p in (ROOT / "cloud-java/backend/target/surefire-reports").glob("TEST-*.xml"):
            if p.stat().st_mtime < test_start - 1: continue
            node = ET.parse(p).getroot()
            for key in ("tests", "failures", "errors", "skipped"): junit[key] += int(node.get(key, "0"))
            junit["reportCount"] += 1
        good = junit["tests"] > 0 and not any(junit[k] for k in ("failures", "errors", "skipped"))
        steps.append({"name": "backend-test-execution", "status": "PASS" if good else "FAIL", "exitCode": 0 if good else 1, **junit})
    end_commit, end_digest = source_state()
    stable = (start_commit, start_digest) == (end_commit, end_digest)
    steps.append({"name": "source-version-stability", "status": "PASS" if stable else "FAIL", "exitCode": 0 if stable else 1,
                  "trackedCodeSha256Before": start_digest, "trackedCodeSha256After": end_digest})
    try:
        status = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, capture_output=True, text=True)
        dirty = bool(status.stdout.strip()) if status.returncode == 0 else None
    except OSError: dirty = None
    source_files = [generator, ROOT / "tests/competition/test_synthetic_dataset.py", Path(__file__).resolve(), ROOT / "scripts/reproduce_qce_patch.py", ROOT / "scripts/reproduce_bridge_smoke.py"]
    result = {"schemaVersion": "1.0", "mode": args.mode, "seed": SEED,
              "status": "PASS" if all(s["status"] == "PASS" for s in steps) else "FAIL",
              "commit": end_commit,
              "workingTreeDirty": dirty, "commitAtStart": start_commit, "commitAtEnd": end_commit,
              "environment": {"python": platform.python_version(), "system": platform.system(), "machine": platform.machine(), "versions": versions},
              "scope": {"syntheticFixtureIntegrity": True, "liveModelEvaluation": False,
                        "externalMessagingLogin": False, "databaseIntegration": args.mode == "full"},
              "sourceHashes": {str(p.relative_to(ROOT)): sha(p) for p in source_files}, "steps": steps, "backendTests": junit}
    (output / "manifest.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    artifact_files = sorted(p for p in output.rglob("*") if p.is_file())
    (output / "SHA256SUMS").write_text("".join(sha(p) + "  " + str(p.relative_to(output)) + "\n" for p in artifact_files))
    print(json.dumps({"status": result["status"], "mode": args.mode, "backendTests": junit}))
    return 0 if result["status"] == "PASS" else 1

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["data-only", "offline", "full"], default="data-only")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=1800, help="Per-command timeout in seconds")
    raise SystemExit(run(parser.parse_args()))
