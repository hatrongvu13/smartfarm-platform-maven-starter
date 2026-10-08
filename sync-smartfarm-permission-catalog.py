#!/usr/bin/env python3
"""Synchronize Gateway SCOPE_* checks to Identity YAML and JwtAuthorities.

Safety rules:
- Add missing permissions only. Never delete YAML permissions.
- Preserve existing KNOWN_SCOPES and add missing Gateway scopes.
- Existing YAML permission metadata is never overwritten.
- Managed YAML entries are delimited by explicit comments.
- Idempotent. Git is the rollback mechanism; no backup files are created.

Usage:
  python3 sync-smartfarm-permission-catalog.py /path/to/repository
  python3 sync-smartfarm-permission-catalog.py /path/to/repository --check
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

AUTHORITY_RE = re.compile(r"hasAuthority\(\s*['\"]SCOPE_([^'\"]+)['\"]\s*\)")
KNOWN_RE = re.compile(
    r"(?P<prefix>\bKNOWN_SCOPES\s*=\s*Set\.of\s*\()"
    r"(?P<body>.*?)"
    r"(?P<suffix>\)\s*;)",
    re.DOTALL,
)
JAVA_STRING_RE = re.compile(r'"((?:\\.|[^"\\])*)"')
YAML_CODE_RE = re.compile(r"^\s*-\s+code:\s*['\"]?([^'\"#\s]+)", re.MULTILINE)

BEGIN = "          # BEGIN AUTO-SYNC GATEWAY PERMISSIONS"
END = "          # END AUTO-SYNC GATEWAY PERMISSIONS"


@dataclass(frozen=True)
class Permission:
    code: str
    resource_type: str
    action: str
    description: str


def required(root: Path, relative: str) -> Path:
    path = root / relative
    if not path.is_file():
        raise SystemExit(f"ERROR: missing required file: {path}")
    return path


def discover_gateway_scopes(source_root: Path) -> set[str]:
    scopes: set[str] = set()
    for path in source_root.rglob("*.java"):
        if any(part in {"target", "build", "generated"} for part in path.parts):
            continue
        scopes.update(AUTHORITY_RE.findall(path.read_text(encoding="utf-8")))
    return scopes


def java_strings(body: str) -> set[str]:
    return {
        bytes(value, "utf-8").decode("unicode_escape")
        for value in JAVA_STRING_RE.findall(body)
    }


def yaml_codes(text: str) -> set[str]:
    return set(YAML_CODE_RE.findall(text))


def permission(scope: str) -> Permission:
    parts = [part for part in scope.split(":") if part]
    action = parts[-1] if parts else "use"
    resource_parts = parts[:-1] or parts
    resource_type = "-".join(resource_parts) if resource_parts else "platform"
    description = f"Allows {action} access to {resource_type.replace('-', ' ')}"
    return Permission(scope, resource_type, action, description)


def quote_yaml(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def render_permissions(scopes: set[str]) -> str:
    lines = [BEGIN]
    for scope in sorted(scopes):
        item = permission(scope)
        lines.extend([
            f"          - code: {quote_yaml(item.code)}",
            f"            resource-type: {quote_yaml(item.resource_type)}",
            f"            action: {quote_yaml(item.action)}",
            f"            description: {quote_yaml(item.description)}",
        ])
    lines.append(END)
    return "\n".join(lines)


def locate_identity_section(text: str) -> tuple[int, int]:
    lines = text.splitlines(keepends=True)
    smartfarm = None
    identity = None
    for i, line in enumerate(lines):
        if line.rstrip("\r\n") == "smartfarm:":
            smartfarm = i
            continue
        if smartfarm is not None and re.match(r"^  identity:\s*(?:#.*)?$", line.rstrip("\r\n")):
            identity = i
            break
    if identity is None:
        raise SystemExit("ERROR: cannot find smartfarm.identity section in application.yml")
    end = len(lines)
    for i in range(identity + 1, len(lines)):
        raw = lines[i].rstrip("\r\n")
        if raw and not raw.startswith(" "):
            end = i
            break
        if re.match(r"^  [A-Za-z0-9_.-]+:\s*(?:#.*)?$", raw):
            end = i
            break
    return identity, end


def authorization_block(scopes: set[str]) -> str:
    return (
        "    authorization:\n"
        "      permission-sync:\n"
        "        enabled: true\n"
        "        update-existing: true\n"
        "        permissions:\n"
        + render_permissions(scopes)
        + "\n"
    )


def patch_yaml(text: str, missing: set[str]) -> str:
    if not missing:
        return text
    if BEGIN in text or END in text:
        if text.count(BEGIN) != 1 or text.count(END) != 1:
            raise SystemExit("ERROR: malformed auto-sync marker block in application.yml")
        start = text.index(BEGIN)
        end = text.index(END, start) + len(END)
        managed = yaml_codes(text[start:end]) | missing
        return text[:start] + render_permissions(managed) + text[end:]

    identity_start, identity_end = locate_identity_section(text)
    lines = text.splitlines(keepends=True)
    insert_at = identity_start + 1
    # Put authorization first inside identity so its location is deterministic.
    lines.insert(insert_at, authorization_block(missing))
    return "".join(lines)


def patch_known_scopes(text: str, gateway_scopes: set[str]) -> str:
    match = KNOWN_RE.search(text)
    if not match:
        raise SystemExit("ERROR: cannot find KNOWN_SCOPES = Set.of(...) in JwtAuthorities.java")
    existing = java_strings(match.group("body"))
    merged = existing | gateway_scopes
    if merged == existing:
        return text
    line_start = text.rfind("\n", 0, match.start()) + 1
    declaration_indent = re.match(r"[ \t]*", text[line_start:match.start()]).group(0)
    item_indent = declaration_indent + "    "
    body = "\n" + "\n".join(
        f'{item_indent}"{scope}"{"," if i < len(merged) - 1 else ""}'
        for i, scope in enumerate(sorted(merged))
    ) + "\n" + declaration_indent
    replacement = match.group("prefix") + body + match.group("suffix")
    return text[:match.start()] + replacement + text[match.end():]


def report(gateway: set[str], yaml: set[str], known: set[str]) -> dict[str, object]:
    return {
        "gatewayScopeCount": len(gateway),
        "yamlPermissionCount": len(yaml),
        "knownScopeCount": len(known),
        "missingInYaml": sorted(gateway - yaml),
        "missingInKnownScopes": sorted(gateway - known),
        "yamlOnlyPermissionsPreserved": sorted(yaml - gateway),
        "knownOnlyScopesPreserved": sorted(known - gateway),
    }


def load_known(text: str) -> set[str]:
    match = KNOWN_RE.search(text)
    if not match:
        raise SystemExit("ERROR: KNOWN_SCOPES is not recognized")
    return java_strings(match.group("body"))


def verify(gateway: set[str], yaml_text: str, known_text: str) -> None:
    yaml = yaml_codes(yaml_text)
    known = load_known(known_text)
    if BEGIN not in yaml_text or END not in yaml_text:
        raise SystemExit("ERROR: YAML managed permission block is missing")
    missing_yaml = gateway - yaml
    missing_known = gateway - known
    if missing_yaml or missing_known:
        raise SystemExit(
            "ERROR: consistency check failed: "
            f"missingInYaml={sorted(missing_yaml)}, "
            f"missingInKnownScopes={sorted(missing_known)}"
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", default=".")
    parser.add_argument("--check", action="store_true", help="Report drift without modifying files")
    args = parser.parse_args()
    root = Path(args.root).expanduser().resolve()

    gateway_root = root / "apps/smartfarm-gateway/src/main/java"
    if not gateway_root.is_dir():
        raise SystemExit(f"ERROR: missing Gateway source: {gateway_root}")
    yaml_path = required(root, "services/smartfarm-identity-service/src/main/resources/application.yml")
    known_path = required(root, "libs/smartfarm-security/src/main/java/com/htv/smartfarm/security/jwt/JwtAuthorities.java")

    gateway = discover_gateway_scopes(gateway_root)
    if not gateway:
        raise SystemExit("ERROR: no Gateway hasAuthority('SCOPE_...') checks found")
    yaml_text = yaml_path.read_text(encoding="utf-8")
    known_text = known_path.read_text(encoding="utf-8")
    before = report(gateway, yaml_codes(yaml_text), load_known(known_text))
    print(json.dumps(before, ensure_ascii=False, indent=2))

    drift = bool(before["missingInYaml"] or before["missingInKnownScopes"])
    markers_missing = BEGIN not in yaml_text or END not in yaml_text
    if args.check:
        if drift or markers_missing:
            print("CHECK FAILED: permission catalog is not synchronized", file=sys.stderr)
            return 1
        print("CHECK OK: Gateway scopes, YAML catalog and KNOWN_SCOPES are synchronized")
        return 0

    updated_yaml = patch_yaml(yaml_text, set(before["missingInYaml"]))
    # First application with no drift still creates an explicit managed block.
    if markers_missing and updated_yaml == yaml_text:
        updated_yaml = patch_yaml(yaml_text, gateway)
    updated_known = patch_known_scopes(known_text, gateway)

    if updated_yaml != yaml_text:
        yaml_path.write_text(updated_yaml, encoding="utf-8")
        print(f"UPDATED {yaml_path.relative_to(root)}")
    else:
        print(f"UNCHANGED {yaml_path.relative_to(root)}")
    if updated_known != known_text:
        known_path.write_text(updated_known, encoding="utf-8")
        print(f"UPDATED {known_path.relative_to(root)}")
    else:
        print(f"UNCHANGED {known_path.relative_to(root)}")

    # Secondary consistency and idempotency checks against actual written files.
    written_yaml = yaml_path.read_text(encoding="utf-8")
    written_known = known_path.read_text(encoding="utf-8")
    verify(gateway, written_yaml, written_known)
    if patch_yaml(written_yaml, gateway - yaml_codes(written_yaml)) != written_yaml:
        raise SystemExit("ERROR: YAML update is not idempotent")
    if patch_known_scopes(written_known, gateway) != written_known:
        raise SystemExit("ERROR: KNOWN_SCOPES update is not idempotent")

    print("VERIFY OK: all Gateway scopes exist in YAML and KNOWN_SCOPES")
    print("NOTE: existing YAML-only permissions and KNOWN_SCOPES were preserved; nothing was deleted")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
