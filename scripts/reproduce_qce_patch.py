#!/usr/bin/env python3
"""Apply the integration patch to the exact upstream revision in a temporary checkout."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

REVISION = "58fc533f1bfdbf4535be0f83af4e595d4d7ecf27"
UPSTREAM = "https://github.com/shuakami/qq-chat-exporter.git"
ROOT = Path(__file__).resolve().parents[1]

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    patches = ROOT / "cloud-java/deploy/qce-patches"
    required = [patches / name for name in ["apply.mjs", "message-media-before.txt", "message-media-after.txt", "docker-entrypoint-qce.sh"]]
    for path in required:
        if not path.is_file(): raise SystemExit(f"Missing integration input: {path.relative_to(ROOT)}")
    def execute(argv, cwd):
        result = subprocess.run(argv, cwd=cwd, capture_output=True, text=True, timeout=300)
        if result.returncode:
            raise SystemExit(result.stderr.replace(str(cwd), "<upstream-checkout>")[-4000:])
        return result.stdout.strip()
    with tempfile.TemporaryDirectory(prefix="zhenglian-qce-") as temp:
        cwd = Path(temp)
        execute(["git", "init", "-q"], cwd)
        execute(["git", "remote", "add", "origin", UPSTREAM], cwd)
        execute(["git", "fetch", "--depth=1", "origin", REVISION], cwd)
        execute(["git", "checkout", "--detach", "FETCH_HEAD"], cwd)
        actual = execute(["git", "rev-parse", "HEAD"], cwd)
        if actual != REVISION: raise SystemExit("Upstream revision mismatch")
        execute(["node", str(patches / "apply.mjs"), str(cwd)], cwd)
        if not (cwd / "YINGGE-MODIFICATIONS.md").is_file(): raise SystemExit("Modification notice missing")
        changed = execute(["git", "diff", "--name-only"], cwd).splitlines()
        report = {"status": "PASS", "upstream": UPSTREAM, "revision": actual,
                  "patchInputHashes": {str(f.relative_to(ROOT)): hashlib.sha256(f.read_bytes()).hexdigest() for f in required},
                  "changedTrackedFiles": changed, "modificationNotice": True,
                  "scope": "Pinned source checkout and integration patch execution; no QQ account login or container image build"}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        print(json.dumps({"status": "PASS", "revision": actual, "changedFiles": len(changed)}))

if __name__ == "__main__": main()
