#!/usr/bin/env python3
"""Synchronize JwtAuthorities.KNOWN_SCOPES with Gateway SCOPE_* checks.

Usage:
  python3 sync-smartfarm-jwt-known-scopes.py /path/to/repository
  python3 sync-smartfarm-jwt-known-scopes.py /path/to/repository --check

The script is idempotent and does not create backups. Git is the rollback mechanism.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

EXPECTED_MISSING = {
    "identity:principal:update",
    "identity:mfa:enroll",
    "identity:mfa:disable",
    "identity:mfa:recovery:regenerate",
    "identity:user:credential:reset",
    "identity:user:mfa:reset",
    "identity:permission:read",
    "health:read",
    "inventory:read",
}

AUTHORITY_RE = re.compile(r"hasAuthority\(\s*['\"]SCOPE_([^'\"]+)['\"]\s*\)")
KNOWN_RE = re.compile(
    r"(?P<prefix>\bKNOWN_SCOPES\s*=\s*(?:Set|List)\.of\s*\()"
    r"(?P<body>.*?)"
    r"(?P<suffix>\)\s*;)",
    re.DOTALL,
)
STRING_RE = re.compile(r'"((?:\\.|[^"\\])*)"')


def discover_gateway_scopes(gateway_java: Path) -> set[str]:
    scopes: set[str] = set()
    for path in gateway_java.rglob("*.java"):
        if any(part in {"target", "build", "generated"} for part in path.parts):
            continue
        text = path.read_text(encoding="utf-8")
        scopes.update(AUTHORITY_RE.findall(text))
    return scopes


def locate_known_scopes(text: str) -> re.Match[str]:
    match = KNOWN_RE.search(text)
    if not match:
        raise RuntimeError(
            "Cannot find KNOWN_SCOPES = Set.of(...) or List.of(...) in JwtAuthorities.java"
        )
    return match


def java_strings(body: str) -> set[str]:
    return {bytes(value, "utf-8").decode("unicode_escape") for value in STRING_RE.findall(body)}


def render_body(scopes: set[str], indent: str) -> str:
    ordered = sorted(scopes)
    if not ordered:
        return ""
    return "\n" + "\n".join(
        f'{indent}"{scope}"{"," if index < len(ordered) - 1 else ""}'
        for index, scope in enumerate(ordered)
    ) + "\n" + indent[:-4]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--check", action="store_true", help="Do not modify files")
    args = parser.parse_args()

    root = Path(args.root).expanduser().resolve()
    gateway_java = root / "apps/smartfarm-gateway/src/main/java"
    authorities = root / "libs/smartfarm-security/src/main/java/com/htv/smartfarm/security/jwt/JwtAuthorities.java"

    missing_paths = [str(path) for path in (gateway_java, authorities) if not path.exists()]
    if missing_paths:
        print("ERROR: required paths do not exist:", file=sys.stderr)
        print("\n".join(missing_paths), file=sys.stderr)
        return 2

    gateway_scopes = discover_gateway_scopes(gateway_java)
    text = authorities.read_text(encoding="utf-8")
    match = locate_known_scopes(text)
    known_scopes = java_strings(match.group("body"))
    missing = gateway_scopes - known_scopes
    stale = known_scopes - gateway_scopes

    report = {
        "gatewayScopeCount": len(gateway_scopes),
        "knownScopeCountBefore": len(known_scopes),
        "missingScopes": sorted(missing),
        "expectedNineMissingDetected": sorted(EXPECTED_MISSING & missing),
        "expectedNineAlreadyPresent": sorted(EXPECTED_MISSING & known_scopes),
        "knownButNotReferencedByGateway": sorted(stale),
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))

    if not missing:
        print("OK: KNOWN_SCOPES covers every Gateway hasAuthority('SCOPE_...') check.")
        return 0

    if args.check:
        print("CHECK FAILED: JwtAuthorities.KNOWN_SCOPES is incomplete.", file=sys.stderr)
        return 1

    # Preserve old scopes because other services may use them even if Gateway does not.
    merged = known_scopes | gateway_scopes
    line_start = text.rfind("\n", 0, match.start()) + 1
    declaration_indent = re.match(r"[ \t]*", text[line_start:match.start()]).group(0)
    item_indent = declaration_indent + "    "
    replacement = match.group("prefix") + render_body(merged, item_indent) + match.group("suffix")
    updated = text[:match.start()] + replacement + text[match.end():]
    authorities.write_text(updated, encoding="utf-8")

    # Secondary consistency check from the actual written file.
    written = authorities.read_text(encoding="utf-8")
    written_known = java_strings(locate_known_scopes(written).group("body"))
    still_missing = gateway_scopes - written_known
    if still_missing:
        print("ERROR: consistency check failed: " + ", ".join(sorted(still_missing)), file=sys.stderr)
        return 3

    print(f"UPDATED: {authorities.relative_to(root)}")
    print(f"ADDED: {', '.join(sorted(missing))}")
    print("VERIFY: all Gateway SCOPE_* checks are now covered by KNOWN_SCOPES.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
