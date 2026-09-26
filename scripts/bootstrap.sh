#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${SMARTFARM_GIT_BASE:-git@github.com:YOUR_ORG}"
MODE="${1:-missing}"
python3 - "$ROOT" "$BASE" "$MODE" <<'PY'
import json, pathlib, subprocess, sys, os
root, base, mode = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
data=json.loads((root/'repositories.json').read_text())
for repo in data['repositories']:
    path=root/repo['path']; url=f"{base}/{repo['name']}.git"
    if path.exists() and any(path.iterdir()):
        if mode == 'pull' and (path/'.git').exists(): subprocess.run(['git','-C',str(path),'pull','--ff-only'],check=True)
        else: print(f"SKIP {repo['path']}")
        continue
    path.parent.mkdir(parents=True,exist_ok=True)
    subprocess.run(['git','clone','--branch',repo['branch'],url,str(path)],check=True)
PY
