#!/usr/bin/env bash
#
# doctor.sh — health check for the SmartFarm monorepo.
#
# Verifies the toolchain and that every module listed in the root reactor POM
# has a pom.xml on disk. This is a single Git repository, so there is no
# repositories.json and nothing to clone — module presence is checked directly
# against the reactor.
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Toolchain"
java -version
mvn -version | head -1

echo "==> Reactor modules"
python3 - "$ROOT" <<'PY'
import pathlib, re, sys
root = pathlib.Path(sys.argv[1])
pom = (root / "pom.xml").read_text(encoding="utf-8")
modules = re.findall(r"<module>\s*([^<]+?)\s*</module>", pom)
missing = [m for m in modules if not (root / m / "pom.xml").is_file()]
print(f"Declared modules: {len(modules)}")
for m in modules:
    mark = "ok" if (root / m / "pom.xml").is_file() else "MISSING"
    print(f"  [{mark}] {m}")
if missing:
    print(f"ERROR: {len(missing)} module POM(s) missing: {missing}")
    raise SystemExit(1)
print("All module POMs present.")
PY

echo "==> doctor OK"
