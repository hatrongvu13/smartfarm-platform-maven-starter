#!/usr/bin/env python3
"""Synchronize SmartFarm SCOPE_* authorities to Identity YAML and JwtAuthorities.

Python 3.9.6 compatible. Add-only and idempotent. It scans Java source under
apps/services/libs/platform, so both Gateway @PreAuthorize checks and service
GrpcMethodPolicy authorities are collected. Order Saga read/admin scopes are
always required.

Usage:
  python3 sync-smartfarm-permission-catalog.py [repository-root]
  python3 sync-smartfarm-permission-catalog.py [repository-root] --check
  python3 sync-smartfarm-permission-catalog.py . --require extra:scope
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

SCOPE_RE = re.compile(r"SCOPE_([A-Za-z0-9*_.:-]+)")
AUTHORITY_RE = re.compile(r"hasAuthority\(\s*['\"](?:SCOPE_)?([^'\"]+)['\"]\s*\)")
KNOWN_RE = re.compile(
    r"(?P<prefix>\bKNOWN_SCOPES\s*=\s*Set\.of\s*\()"
    r"(?P<body>.*?)"
    r"(?P<suffix>\)\s*;)",
    re.DOTALL,
)
JAVA_STRING_RE = re.compile(r'"((?:\\.|[^"\\])*)"')
YAML_CODE_RE = re.compile(r"^\s*-\s+code:\s*['\"]?([^'\"#\s]+)", re.MULTILINE)

BEGIN = "          # BEGIN AUTO-SYNC SMARTFARM PERMISSIONS"
END = "          # END AUTO-SYNC SMARTFARM PERMISSIONS"
LEGACY_BEGIN = "          # BEGIN AUTO-SYNC GATEWAY PERMISSIONS"
LEGACY_END = "          # END AUTO-SYNC GATEWAY PERMISSIONS"
DEFAULT_REQUIRED = {"orders:saga:read", "orders:saga:admin"}
EXCLUDED = {
    ".git", ".idea", ".vscode", "target", "build", "dist", "generated",
    "generated-sources", "node_modules", "out",
}


@dataclass(frozen=True)
class Permission:
    code: str
    resource_type: str
    action: str
    description: str


def read_text(path: Path) -> str:
    with path.open("r", encoding="utf-8") as stream:
        return stream.read()


def write_text(path: Path, text: str) -> None:
    # Path.write_text(newline=...) is unavailable in Python 3.9.
    with path.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write(text)


def required_file(root: Path, relative: str) -> Path:
    path = root / relative
    if not path.is_file():
        raise SystemExit("ERROR: missing required file: %s" % path)
    return path


def discover_scopes(root: Path) -> Tuple[Set[str], Dict[str, List[str]]]:
    scopes: Set[str] = set()
    origins: Dict[str, List[str]] = {}
    for directory in ("apps", "services", "libs", "platform"):
        source = root / directory
        if not source.is_dir():
            continue
        for path in source.rglob("*.java"):
            if any(part in EXCLUDED for part in path.parts):
                continue
            text = read_text(path)
            discovered = set(SCOPE_RE.findall(text))
            discovered.update(AUTHORITY_RE.findall(text))
            for scope in sorted(value.strip() for value in discovered if value.strip()):
                if scope.startswith("$"):
                    continue
                scopes.add(scope)
                origins.setdefault(scope, []).append(str(path.relative_to(root)))
    return scopes, origins


def java_strings(body: str) -> Set[str]:
    return set(JAVA_STRING_RE.findall(body))


def yaml_codes(text: str) -> Set[str]:
    return set(YAML_CODE_RE.findall(text))


def load_known(text: str) -> Set[str]:
    match = KNOWN_RE.search(text)
    if not match:
        raise SystemExit("ERROR: KNOWN_SCOPES = Set.of(...) not found")
    return java_strings(match.group("body"))


def permission(scope: str) -> Permission:
    parts = [part for part in scope.split(":") if part]
    action = parts[-1] if parts else "use"
    resource_type = "-".join(parts[:-1] or parts) or "platform"
    descriptions = {
        "orders:saga:read": "View Order Saga execution progress",
        "orders:saga:admin": "Administer Order Saga recovery operations",
        "inventory:read": "Read inventory, warehouse, item, and stock data",
        "inventory:write": "Manage inventory, warehouse, item, and stock data",
    }
    description = descriptions.get(
        scope,
        "Allows %s access to %s" % (action, resource_type.replace("-", " ")),
    )
    return Permission(scope, resource_type, action, description)


def quote_yaml(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def render_permissions(scopes: Set[str]) -> str:
    lines = [BEGIN]
    for scope in sorted(scopes):
        item = permission(scope)
        lines.extend([
            "          - code: %s" % quote_yaml(item.code),
            "            resource-type: %s" % quote_yaml(item.resource_type),
            "            action: %s" % quote_yaml(item.action),
            "            description: %s" % quote_yaml(item.description),
        ])
    lines.append(END)
    return "\n".join(lines)


def managed_markers(text: str) -> Optional[Tuple[str, str]]:
    current = BEGIN in text or END in text
    legacy = LEGACY_BEGIN in text or LEGACY_END in text
    if current and legacy:
        raise SystemExit("ERROR: current and legacy managed blocks both exist")
    if current:
        if text.count(BEGIN) != 1 or text.count(END) != 1:
            raise SystemExit("ERROR: malformed managed permission block")
        return BEGIN, END
    if legacy:
        if text.count(LEGACY_BEGIN) != 1 or text.count(LEGACY_END) != 1:
            raise SystemExit("ERROR: malformed legacy managed permission block")
        return LEGACY_BEGIN, LEGACY_END
    return None


def locate_identity_line(text: str) -> int:
    lines = text.splitlines(keepends=True)
    smartfarm: Optional[int] = None
    for index, line in enumerate(lines):
        raw = line.rstrip("\r\n")
        if raw == "smartfarm:":
            smartfarm = index
        elif smartfarm is not None and re.match(r"^  identity:\s*(?:#.*)?$", raw):
            return index
    raise SystemExit("ERROR: smartfarm.identity section not found")


def authorization_block(scopes: Set[str]) -> str:
    return (
        "    authorization:\n"
        "      permission-sync:\n"
        "        enabled: true\n"
        "        update-existing: true\n"
        "        permissions:\n"
        + render_permissions(scopes)
        + "\n"
    )


def patch_yaml(text: str, scopes: Set[str]) -> str:
    markers = managed_markers(text)
    if markers:
        begin, end = markers
        start = text.index(begin)
        stop = text.index(end, start) + len(end)
        merged = yaml_codes(text[start:stop]) | scopes
        return text[:start] + render_permissions(merged) + text[stop:]
    lines = text.splitlines(keepends=True)
    lines.insert(locate_identity_line(text) + 1, authorization_block(scopes))
    return "".join(lines)


def patch_known(text: str, scopes: Set[str]) -> str:
    match = KNOWN_RE.search(text)
    if not match:
        raise SystemExit("ERROR: KNOWN_SCOPES = Set.of(...) not found")
    existing = load_known(text)
    merged = existing | scopes
    if merged == existing:
        return text
    line_start = text.rfind("\n", 0, match.start()) + 1
    indent_match = re.match(r"[ \t]*", text[line_start:match.start()])
    indent = indent_match.group(0) if indent_match else ""
    item_indent = indent + "    "
    ordered = sorted(merged)
    body = "\n" + "\n".join(
        '%s"%s"%s' % (item_indent, scope, "," if index < len(ordered) - 1 else "")
        for index, scope in enumerate(ordered)
    ) + "\n" + indent
    replacement = match.group("prefix") + body + match.group("suffix")
    return text[:match.start()] + replacement + text[match.end():]


def create_report(
    discovered: Set[str],
    effective: Set[str],
    yaml_text: str,
    known_text: str,
    origins: Dict[str, List[str]],
) -> Dict[str, object]:
    yaml = yaml_codes(yaml_text)
    known = load_known(known_text)
    return {
        "discoveredScopeCount": len(discovered),
        "effectiveScopeCount": len(effective),
        "missingInYaml": sorted(effective - yaml),
        "missingInKnownScopes": sorted(effective - known),
        "yamlOnlyPermissionsPreserved": sorted(yaml - effective),
        "knownOnlyScopesPreserved": sorted(known - effective),
        "origins": {
            scope: origins.get(scope, ["<required-default>"])
            for scope in sorted(effective)
        },
    }


def verify(effective: Set[str], yaml_text: str, known_text: str) -> None:
    if managed_markers(yaml_text) is None:
        raise SystemExit("ERROR: managed YAML permission block is missing")
    missing_yaml = effective - yaml_codes(yaml_text)
    missing_known = effective - load_known(known_text)
    if missing_yaml or missing_known:
        raise SystemExit(
            "ERROR: permission consistency failed: missingInYaml=%s, missingInKnownScopes=%s"
            % (sorted(missing_yaml), sorted(missing_known))
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--require", action="append", default=[], metavar="SCOPE")
    parser.add_argument("--report-file", default="", help="Optional JSON report path")
    args = parser.parse_args()

    root = Path(args.root).expanduser().resolve()
    if not (root / "pom.xml").is_file():
        raise SystemExit("ERROR: repository root must contain pom.xml: %s" % root)

    yaml_path = required_file(
        root, "services/smartfarm-identity-service/src/main/resources/application.yml"
    )
    known_path = required_file(
        root,
        "libs/smartfarm-security/src/main/java/com/htv/smartfarm/security/jwt/JwtAuthorities.java",
    )

    discovered, origins = discover_scopes(root)
    extra = {value.strip() for value in args.require if value.strip()}
    effective = discovered | DEFAULT_REQUIRED | extra
    yaml_text = read_text(yaml_path)
    known_text = read_text(known_path)
    before = create_report(discovered, effective, yaml_text, known_text, origins)
    print(json.dumps(before, ensure_ascii=False, indent=2))

    if args.report_file:
        report_path = root / args.report_file
        report_path.parent.mkdir(parents=True, exist_ok=True)
        write_text(report_path, json.dumps(before, ensure_ascii=False, indent=2) + "\n")
        print("REPORT %s" % report_path.relative_to(root))

    drift = bool(before["missingInYaml"] or before["missingInKnownScopes"])
    markers_missing = managed_markers(yaml_text) is None
    if args.check:
        if drift or markers_missing:
            print("CHECK FAILED: permission catalog is not synchronized", file=sys.stderr)
            return 1
        print("CHECK OK: Java authorities, YAML and KNOWN_SCOPES are synchronized")
        return 0

    updated_yaml = patch_yaml(yaml_text, effective)
    updated_known = patch_known(known_text, effective)

    if updated_yaml != yaml_text:
        write_text(yaml_path, updated_yaml)
        print("UPDATED %s" % yaml_path.relative_to(root))
    else:
        print("UNCHANGED %s" % yaml_path.relative_to(root))
    if updated_known != known_text:
        write_text(known_path, updated_known)
        print("UPDATED %s" % known_path.relative_to(root))
    else:
        print("UNCHANGED %s" % known_path.relative_to(root))

    written_yaml = read_text(yaml_path)
    written_known = read_text(known_path)
    verify(effective, written_yaml, written_known)
    if patch_yaml(written_yaml, effective) != written_yaml:
        raise SystemExit("ERROR: YAML update is not idempotent")
    if patch_known(written_known, effective) != written_known:
        raise SystemExit("ERROR: KNOWN_SCOPES update is not idempotent")

    print("VERIFY OK: all discovered and required scopes exist in YAML and KNOWN_SCOPES")
    print("NEXT: restart Identity and Gateway, then sign in again to refresh tokens")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
