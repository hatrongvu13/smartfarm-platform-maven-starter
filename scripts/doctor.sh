#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
java -version
mvn -version
python3 - "$ROOT" <<'PY'
import json,pathlib,sys
root=pathlib.Path(sys.argv[1]); repos=json.loads((root/'repositories.json').read_text())['repositories']
missing=[r['path'] for r in repos if not (root/r['path']/'pom.xml').is_file()]
print(f"Maven modules: {len(repos)}; missing POMs: {missing}")
raise SystemExit(bool(missing))
PY
