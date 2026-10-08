# PROJECT STATUS

> Ma trận hoàn thiện (spec §23). Nguồn: `docs/audit/00-08`, `docs/cleanup/00-12`, graph `graphify-out/`.
> Incremental 2026-10-03 (HEAD `51f23a7`). Nhãn trạng thái theo spec §3.

## Completion matrix

| Area | Status | Completion | Blocker | Next Action |
|---|---|---:|---|---|
| Architecture (modular monolith→services) | PARTIAL | 85% | order-service coupling (god-nodes) | giữ nguyên; theo dõi, không rewrite |
| gRPC / Proto contracts | COMPLETE | 95% | — | giữ; thêm contract test |
| Event-flow (outbox/inbox MQTT) | PARTIAL | 92% | — (EVT-01/02/03 fixed; ISSUE-06 by-design) | ISSUE-10/13 polish |
| Database / Flyway | COMPLETE | 100% | — | — |
| Security (JWT/RBAC/MFA gRPC) | COMPLETE | 90% | — | giữ |
| Security (MQTT message integrity) | PARTIAL | 75% | broker ACL/mTLS chưa enable (app-level HMAC đã live-verified) | bật broker auth/TLS prod |
| Testing | PARTIAL | 72% | thiếu runtime IT Inventory/Finance thật | contract/invariant tests Order; mở rộng IT |
| Infrastructure (compose/k8s/obs) | PARTIAL | 75% | RISK-BROKER-01 (MQTT prod hardening) | cấu hình broker prod |
| Runtime config | COMPLETE | 100% | — | CFG-01 resolved; giữ nested `jwt.*` |
| Reporting (non-livestock types) | PARTIAL | 40% | read-models + object-storage resolver | backlog |
| V2 Warehouse plane | NOT_STARTED | 0% | BL-01 (new engagement) | defer |
| V3 Automation plane | NOT_STARTED | 0% | BL-02 (new engagement) | defer |
| farm-simulator | SKELETON (quarantined) | N/A | DEFERRED (`@Profile dev&!prod`) | giữ quarantine |
| Documentation | PARTIAL | 80% | — | cập nhật incremental (file này) |

## 42.1 Project hiện tại là gì?
```
Architecture:    Spring Boot modular services monorepo (reactor), gRPC nội bộ + gateway edge + MQTT event bus
Modules:         15 (7 service, 4 lib, 2 app, 1 reactor)
Services:        identity, order, finance, inventory, livestock, health, reporting (+ readiness)
Main tech:       Java 17, Spring Boot 4.1.1, spring-grpc, Paho/Mosquitto MQTT, Postgres 17, Redis, Flyway
Runtime:         compose.yaml (dev) + deploy/kubernetes (prod) + observability stack
```

## 42.2 Đã làm được gì? (IMPLEMENTED/TESTED)
Order persistent saga (forward+compensation, worker path) · outbox at-least-once · CQRS ordered projection +
gap recovery · identity auth/RBAC/MFA-TOTP/service-token/MQTT command inbox idempotent · libs/security
JwtServerInterceptor fail-closed + GrpcMethodPolicy per-method · finance real ledger + idempotent compensation ·
inventory domain/gRPC/MQTT inbox + ITs · livestock task lifecycle + outbox · health Flyway-owned schema ·
messaging dispatch classifier + exponential backoff · gateway MQTT→WS bus · readiness SSRF-safe.

## 42.3 Đang làm gì? (PARTIAL/IN_PROGRESS)
MQTT broker hardening (ISSUE-02 broker ACL/mTLS; app-level HMAC đã xong) · reporting non-livestock report types (placeholder+logged) ·
test coverage mở rộng.

## 42.4 Chưa làm gì? (PLANNED/SKELETON/NOT_STARTED)
V2 warehouse plane (BL-01) · V3 automation plane (BL-02) · farm-simulator hoàn chỉnh (SKELETON, quarantined).

## 42.5 Có lỗi gì? (BROKEN/BLOCKED)
- **CFG-01 (HIGH, NEW)** — `test` profile không boot ở 6 service (flat security key). BLOCKED cho ai chạy profile `test`.
- Tech-debt mở: ISSUE-10 (publisher session, broker-dependent), ISSUE-11 (gRPC audience), ISSUE-13 circuit-breaker. ISSUE-03/04/05 đã resolved.

## 42.6 Vì sao chưa thể chạy (hoàn toàn)?
Prod CHẠY được (ISSUE-01 vá). Hạn chế duy nhất mới: profile `test` BLOCKED bởi CFG-01. MQTT prod chưa hardened (RISK-BROKER-01).

## 42.7 Muốn chạy được cần làm gì? (P0/P1)
`V0.1-001` (CFG-01, P0 cho CI/test) → xác nhận env prod đầy đủ (P1) → MQTT broker hardening (P1, RISK-BROKER-01).

## 42.8 Sau khi chạy được? (P2/P3/P4)
P2: bật broker ACL/mTLS (ISSUE-02, HMAC app-level đã xong), audit-log super-admin (ISSUE-12), mở rộng IT. P3: reporting read-models, giảm coupling order. P4: V2/V3 planes.

## 42.9 Task tiếp theo (chưa bị block)
**Order hardening:** tenant-scoped stale-claim administration + Saga contract tests; broker ACL/TLS remains the production infrastructure gate.


---

## Status update — 2026-10-07 (after auth/security fix pass)

### Verified complete this pass (live against dev)
- **Identity/auth hardening**: endpoint-based first super-admin (`POST /api/v1/auth/bootstrap-superadmin`,
  one-shot, 409 after), email+password login (tenantId optional; tenant list when >1),
  super-admin wildcard `*` recognised at the gateway, TOTP enrollment working.
- **New FE signal**: `GET /api/v1/platform/deployment-state` → `{initialized, superAdminExists,
  bootstrapRequired}` (public, pre-login).
- **Single-ingress**: every service binds HTTP + gRPC to loopback by default (dev + prod); gateway is
  the sole public ingress.
- **Bug fixes landed**: MQTT health-indicator NPE; `jackson-datatype-jsr310` (unblocked ALL integration
  event publishing); TOTP pending-enrollment supersede; bootstrap transaction isolation (409 correct).
- **Cleanup**: 6 superseded manual curl scripts archived to `scripts/legacy/`; Bruno API collection added;
  docs portal + links fixed.

### Where the project stands
| Area | State |
|------|-------|
| Core domain (order saga, inventory, finance ledger, livestock, identity RBAC/MFA) | **Implemented**, gRPC internal |
| REST↔gRPC boundary | **Compliant** — internal business = gRPC; REST = edge/auth/actuator/dev/ops-admin |
| Proto centralization (`libs/smartfarm-proto`) | **Done** (12 protos, versioned, 0 dup) |
| Shared libs (proto/security/common-kernel/messaging) | **Done** |
| Auth / first-deployment lifecycle | **Done** this pass |
| Prod single-ingress binding | **Done** this pass |

### Remaining to complete (prioritised)
- **P1 (prod-blocking security)**: ISSUE-02 — broker-level auth/TLS/ACL. App-level HMAC sign→verify is
  **DONE + live-verified** (2026-10-07, part 3); broker `allow_anonymous true` + mTLS/ACL still sample-only.
- **P2**: ISSUE-12 (audit-log super-admin actions), GW-02 (finance only dev-only GraphQL, no prod path),
  live-verify of GW-01/EVT-03 (needs health + gateway restart).
- **P3/P4**: ISSUE-10/11/13 (MQTT client-id/session, per-service audience, externalize gRPC deadlines +
  circuit-breaker), ISSUE-15 (Flyway-only ddl), ISSUE-16 (externalize dev secrets).
- **Infra**: k8s manifest exists only for order+gateway — the other services need manifests for a full
  prod deploy.

### Resolved 2026-10-07 (event/gateway pass)
- **EVT-01/02 (DONE, live-verified)**: identity now emits protobuf `DomainEvent`/`IdentityLifecycleEvent`
  on the standard topic; reaches the gateway WS bridge.
- **EVT-03 (DONE, build+test)**: health event publisher active in all profiles, signed, prod-capable
  (Option A).
- **GW-01 (DONE, build+test)**: health wired into the gateway (dev REST facade `/api/v1/health/*`).
- **ISSUE-06 (by-design)**: `identity.command.result` is the WS-facing command-ack, not an orphan bug.
- **Gateway bugfix**: `finance.grpc-port` corrected `9097→9094`.

Full open-issue detail: `docs/audit/unresolved-items.md`.