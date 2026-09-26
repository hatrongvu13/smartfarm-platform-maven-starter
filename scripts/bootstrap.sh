#!/usr/bin/env bash
#
# bootstrap.sh — one-time setup for the SmartFarm monorepo.
#
# This is now a SINGLE Git repository (a Maven multi-module monorepo). There are
# no sub-repositories to clone: every module lives in this tree and is built by
# the reactor from the root POM. This script just verifies the toolchain and
# does a first full build so downstream `spring-boot:run` commands work.
#
# Usage:
#   ./scripts/bootstrap.sh            # verify tools + full clean install
#   ./scripts/bootstrap.sh --skip-build   # verify tools only
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> SmartFarm monorepo bootstrap"
echo "    root: $ROOT"

# --- toolchain checks ---
command -v java >/dev/null 2>&1 || { echo "ERROR: java not found (need JDK 17+)"; exit 1; }
command -v mvn  >/dev/null 2>&1 || { echo "ERROR: mvn not found (need Maven 3.9+)"; exit 1; }
java -version
mvn -version | head -1

if [[ "${1:-}" == "--skip-build" ]]; then
    echo "==> --skip-build given; toolchain OK, skipping build."
    exit 0
fi

# --- first full build (installs internal modules into the local repo) ---
echo "==> Building the full reactor (mvn clean install)…"
mvn -B clean install

cat <<'EOF'

==> Bootstrap complete.

Run individual services after this install, e.g.:
  mvn -f services/smartfarm-livestock-service/pom.xml spring-boot:run
  mvn -f apps/smartfarm-gateway/pom.xml spring-boot:run

See the root README.md for ports and the full run guide.
EOF
