# Graphify Snapshot v0

```
Commit:            51f23a7 "v1 start" (graph artefacts generated at prior HEAD; structure unchanged since)
Branch:            main
Date:              2026-10-02 15:56 (graph.json mtime)
Graphify version:  CLI at /Users/jaxmac/.local/bin/graphify
Scope:             whole monorepo src/main (services + libs + apps)
Modules scanned:   15 (7 services, 4 libs, 2 apps, 1 reactor aggregator)
Nodes / edges:     4509 nodes / 13727 edges
Artifacts:         graphify-out/graph.json, manifest.json, .graphify_analysis.json
```

## Edge relation counts
`references 3752 · calls 3496 · imports 3059 · method 2342 · contains 680 · imports_from 88 · inherits 69 · defines 48 · depends_on 32 · implements 23 · case_of 135`

## God-nodes (high fan-in) — bottleneck watch
| Symbol | Degree | Module |
|---|---:|---|
| `OrderEntity` | 94 | order-service |
| `OrderSagaStepEntity` | 67 | order-service |
| `OrderSagaEntity` | 63 | order-service |
| `UserAccountEntity` | 60 | identity-service |
| `InventoryRepository` | 53 | inventory-service |
| `TaskEntity` | 53 | livestock-service |
| `TenantMembershipEntity` | 53 | identity-service |
| `OrderOutboxEntity` | 50 | order-service |
| `OrderSagaTransactionService` | 49 | order-service |
| `OrderProjectionGapEntity` | 46 | order-service |

## Graph "surprises" (cross-module edges to interpret)
- `farm-simulator → common-kernel` và `farm-simulator → proto` — simulator (DEFERRED/quarantined, `@Profile("dev & !prod")`).
- `GrpcCaller → TokenType` — cầu nối cộng đồng identity ↔ security.
- `gateway → common-kernel / proto` — edge tier hợp lệ.

## Validity note
Delta `51f23a7` chỉ chạm **config + test**, không chạm `src/main` source. Vì graph được build trên
`src/main`, snapshot v0 vẫn **current**. Chưa cần `snapshot-v1`. Khi code `src/main` đổi → regenerate
và tạo `graph-diff.md`.
