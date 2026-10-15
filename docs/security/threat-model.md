# Threat Model — SmartFarm Platform

> Sơ bộ, reconciled 2026-10-15 against current source and configuration. Runtime deployment settings must be verified independently.

## Trust boundaries
1. **Internet → Gateway** (:8080): untrusted client. Mitigation: JWT RS256 + audience/tenant, permitAll chỉ auth/health/docs/ws.
2. **Gateway → Identity** (HTTP auth and internal service-token endpoint; gRPC): semi-trusted internal. The HTTP token endpoint requires a shared application header plus client credentials; gRPC issue/exchange require a caller JWT. **Gap**: these are duplicate issuance paths, gRPC TLS is off by default, and the latest local Identity startup reported `clientCount=0`, so successful local token issuance is unverified.
3. **Gateway → other services** (gRPC): semi-trusted internal. Mitigation: service token per audience, gRPC fail-closed authz. **Gap**: TLS off (plaintext).
4. **Services ↔ MQTT broker**: App-level HMAC envelope implementation exists; dev profiles enable it and production profile defaults are off. **Gap**: broker `allow_anonymous true`, no TLS/ACL.
5. **Services → databases/cache**: Mitigation: credentials via environment in production. **Gap**: development defaults are present in source; persistence/cache configuration varies by module and profile.

## Threats & status
| # | Threat | Vector | Mitigation | Status |
|---|--------|--------|-----------|:------:|
| T1 | Token forgery | giả JWT | RS256 + JWKS + audience/tenant | ✅ mitigated |
| T2 | Token replay/theft | refresh reuse | rotating + family reuse-detection | ✅ |
| T3 | Brute-force login | credential stuffing | lockout + timing defense | ✅ |
| T4 | MFA bypass | challenge brute | hashed challenge + lockout | ✅ |
| T5 | Internal gRPC sniffing/MITM | plaintext gRPC | — | ❌ TLS off (DESIGNED) |
| T6 | MQTT spoofed event | anonymous publish | HMAC code/profile configuration; ACL/TLS sample only | ⚠️ profile-dependent app-level control; broker hardening open |
| T7 | Privilege escalation | super-admin SCOPE_* | RBAC | ⚠️ bypass path, no audit-log (ISSUE-12) |
| T8 | Event payload injection | malformed event | protobuf typing and HMAC verification code | ⚠️ handler/configuration coverage is not a live deployment proof |
| T9 | Secret leak | dev secret in repo | env prod | ⚠️ dev-only (ISSUE-16) |
| T10 | Service-token issuance unavailable or exposed through duplicate routes | client allowlist not resolved at runtime / HTTP endpoint reachable beyond trusted network | HTTP shared header and client credentials; gRPC caller JWT | ⚠️ local client binding unverified; TLS/mTLS and transport consolidation remain |

## Ưu tiên khắc phục
P2: T6 (MQTT hardening), T7 (super-admin audit). P2/P3: T5 (gRPC TLS). V1: T8 (identity protobuf).

← [Security Architecture](security-architecture.md) · [Identity backlog](../../IDENTITY_BACKLOG_UPDATED.md)
