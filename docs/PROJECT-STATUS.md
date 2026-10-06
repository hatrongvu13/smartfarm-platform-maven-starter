# PROJECT STATUS

> Ma trận hoàn thiện (spec §23). Nguồn: `docs/audit/00-08`, `docs/cleanup/00-12`, graph `graphify-out/`.
> Incremental 2026-10-03 (HEAD `51f23a7`). Nhãn trạng thái theo spec §3.

## Completion matrix

| Area | Status | Completion | Blocker | Next Action |
|---|---|---:|---|---|
| Architecture (modular monolith→services) | PARTIAL | 85% | order-service coupling (god-nodes) | giữ nguyên; theo dõi, không rewrite |
| gRPC / Proto contracts | COMPLETE | 95% | — | giữ; thêm contract test |
| Event-flow (outbox/inbox MQTT) | PARTIAL | 85% | ISSUE-06 identity command-result orphan | khép consumer |
| Database / Flyway | COMPLETE | 100% | — | — |
| Security (JWT/RBAC/MFA gRPC) | COMPLETE | 90% | — | giữ |
| Security (MQTT message integrity) | PARTIAL | 50% | ISSUE-02 wiring (HMAC default-off, chưa nối publisher/consumer) | nối verifier + bật ACL/mTLS |
| Testing | PARTIAL | 65% | thêm IT cross-service / contract | mở rộng coverage |
| Infrastructure (compose/k8s/obs) | PARTIAL | 75% | RISK-BROKER-01 (MQTT prod hardening) | cấu hình broker prod |
| Runtime config | PARTIAL | 90% | **CFG-01** (test profile flat key x6) | vá application-test.yml |
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
MQTT message-integrity wiring (ISSUE-02) · reporting non-livestock report types (placeholder+logged) ·
test coverage mở rộng · identity command-result flow (ISSUE-06 orphan).

## 42.4 Chưa làm gì? (PLANNED/SKELETON/NOT_STARTED)
V2 warehouse plane (BL-01) · V3 automation plane (BL-02) · farm-simulator hoàn chỉnh (SKELETON, quarantined).

## 42.5 Có lỗi gì? (BROKEN/BLOCKED)
- **CFG-01 (HIGH, NEW)** — `test` profile không boot ở 6 service (flat security key). BLOCKED cho ai chạy profile `test`.
- Tech-debt mở: ISSUE-03 (dual saga key convention) · ISSUE-04/05 (head-of-line blocking, consumer persistence) — không chặn runtime.

## 42.6 Vì sao chưa thể chạy (hoàn toàn)?
Prod CHẠY được (ISSUE-01 vá). Hạn chế duy nhất mới: profile `test` BLOCKED bởi CFG-01. MQTT prod chưa hardened (RISK-BROKER-01).

## 42.7 Muốn chạy được cần làm gì? (P0/P1)
`V0.1-001` (CFG-01, P0 cho CI/test) → xác nhận env prod đầy đủ (P1) → MQTT broker hardening (P1, RISK-BROKER-01).

## 42.8 Sau khi chạy được? (P2/P3/P4)
P2: nối MQTT HMAC (ISSUE-02), khép command-result (ISSUE-06), mở rộng IT. P3: reporting read-models, giảm coupling order. P4: V2/V3 planes.

## 42.9 Task tiếp theo (chưa bị block)
**`V0.1-001` — vá 6 `application-test.yml` sang nested `smartfarm.security.jwt.*`.** Độc lập, không phụ thuộc.


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
- **P1 (prod-blocking security)**: ISSUE-02 — enable MQTT HMAC sign→verify + broker auth/TLS/ACL
  (currently impl present but default-off, `allow_anonymous true`).
- **P1 (integration gaps)**: EVT-01 (identity events are JSON, others protobuf → WS bridge drops them);
  EVT-03 + GW-01 (health-service has no prod event path and is not wired into the gateway at all).
- **P2**: ISSUE-06 (orphan `identity.command.result` producer), ISSUE-12 (audit-log super-admin actions),
  GW-02 (finance only dev-only GraphQL, no prod path).
- **P3/P4**: ISSUE-10/11/13 (MQTT client-id/session, per-service audience, externalize gRPC deadlines +
  circuit-breaker), ISSUE-15 (Flyway-only ddl), ISSUE-16 (externalize dev secrets).
- **Infra**: k8s manifest exists only for order+gateway — the other services need manifests for a full
  prod deploy.

Full open-issue detail: `docs/audit/unresolved-items.md`.