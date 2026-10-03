# Graphify Findings

> Graphify là công cụ định vị/phân vùng, KHÔNG phải source of truth. Khi Graphify khác source: `SOURCE_CODE_WINS`.

## Graph ↔ Source mismatches
Lần incremental này **không phát sinh** `GRAPH_SOURCE_MISMATCH` mới: delta chỉ chạm config/test, không
chạm `src/main` → graph vẫn khớp source.

Mẫu ghi nhận (dùng cho lần sau nếu phát hiện):
```
Graphify:   A -> B
Source:     A -> C
Resolution: Source is current source of truth.
Action:     Update graph / rescan.
```

## Carry-forward findings từ baseline (vẫn đúng)
- **god-nodes order-service** (`OrderEntity` deg 94, saga step/entity 67/63, outbox 50) — tập trung coupling,
  là bottleneck/rủi ro vận hành cao nhất. Chi tiết: `docs/audit/01-architecture-graph-report.md`.
- **farm-simulator cross-module edges** — module DEFERRED/quarantined; edge tới common-kernel/proto là hợp lệ
  nhưng simulator không được bật ở prod (`@Profile("dev & !prod")`, SKEL-01).
- **GrpcCaller→TokenType** — cầu identity↔security hợp lệ (TokenVerifier dùng chung).

## Startup critical path (từ graph, spec §31)
```
Application context
  └─ SmartFarmSecurityProperties (jwt.*)   ← nếu sai schema => BLOCKED (xem CFG-01 / ISSUE-01)
  └─ DataSource + Flyway (prod: validate)
  └─ Repository layer
  └─ Domain/Application service
  └─ gRPC server (JwtServerInterceptor fail-closed)
  └─ MQTT publisher/consumer (outbox/inbox)
```
Node BROKEN trên path này ⇒ `Runtime Status = BLOCKED`. Hiện `prod` của order-service dính ISSUE-01
(flat key) và profile `test` của 6 service dính CFG-01 (cùng pattern).
