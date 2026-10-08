# Threat Model — SmartFarm Platform

> Sơ bộ, dựa trên source @ HEAD `eaaa112`. Phạm vi: edge gateway, inter-service gRPC, MQTT event bus, identity.

## Trust boundaries
1. **Internet → Gateway** (:8080): untrusted client. Mitigation: JWT RS256 + audience/tenant, permitAll chỉ auth/health/docs/ws.
2. **Gateway → Services** (gRPC): semi-trusted internal. Mitigation: service token per audience, gRPC fail-closed authz. **Gap**: TLS off (plaintext).
3. **Services ↔ MQTT broker**: App-level HMAC envelope is implemented and live-verified. **Gap**: broker `allow_anonymous true`, no TLS/ACL.
4. **Services → Postgres/Redis**: Mitigation: credential qua env (prod). **Gap**: dev default secret trong repo.

## Threats & status
| # | Threat | Vector | Mitigation | Status |
|---|--------|--------|-----------|:------:|
| T1 | Token forgery | giả JWT | RS256 + JWKS + audience/tenant | ✅ mitigated |
| T2 | Token replay/theft | refresh reuse | rotating + family reuse-detection | ✅ |
| T3 | Brute-force login | credential stuffing | lockout + timing defense | ✅ |
| T4 | MFA bypass | challenge brute | hashed challenge + lockout | ✅ |
| T5 | Internal gRPC sniffing/MITM | plaintext gRPC | — | ❌ TLS off (DESIGNED) |
| T6 | MQTT spoofed event | anonymous publish | HMAC envelope live-verified; ACL/TLS sample | ⚠️ app-level mitigated, broker hardening open |
| T7 | Privilege escalation | super-admin SCOPE_* | RBAC | ⚠️ bypass path, no audit-log (ISSUE-12) |
| T8 | Event payload injection | malformed event | protobuf typing + HMAC verification | ✅ EVT-01/02 resolved and live-verified |
| T9 | Secret leak | dev secret in repo | env prod | ⚠️ dev-only (ISSUE-16) |

## Ưu tiên khắc phục
P2: T6 (MQTT hardening), T7 (super-admin audit). P2/P3: T5 (gRPC TLS). V1: T8 (identity protobuf).

← [Security Architecture](security-architecture.md) · [Unresolved Items](../audit/unresolved-items.md)
