# Service Communications — SmartFarm Platform

> Verified @ HEAD `eaaa112`. Ma trận giao tiếp thực tế (có lời gọi trong source).

## Ma trận (from → to)
| From → To | Protocol | Chi tiết | Status |
|-----------|----------|----------|:------:|
| client → gateway | REST + GraphQL + WS | edge ingress :8080 | 🟢 |
| gateway → identity | REST (auth) + gRPC | auth proxy + directory/admin/credential | 🟢 |
| gateway → order | gRPC | order + saga admin | 🟢 |
| gateway → inventory | gRPC | dev-only facade | 🟡 |
| gateway → reporting | gRPC | dev-only facade | 🟡 |
| gateway → finance | gRPC | 2 dev-only GraphQL | 🟡 |
| gateway → livestock | gRPC | dev-only facade | 🟡 |
| gateway → health | — | **không có** (GW-01) | 🔴 |
| order → inventory | gRPC | ReserveStock, CommitReservation, ReleaseReservation (saga) | 🟢 |
| order → finance | gRPC | RecordExpense, ReverseExpense (saga) | 🟢 |
| order → MQTT | event (outbox) | OrderChanged protobuf | 🟢 |
| livestock → MQTT | event (outbox) | task-* protobuf | 🟢 |
| health → MQTT | event (outbox) | health-* protobuf (dev-only, loopback) | 🟡 |
| identity → MQTT | event (outbox) | **JSON** (mismatch) | 🔴 |
| MQTT → inventory | event (sub) | task-changed/v1 only | 🟡 |
| MQTT → order | event (sub) | OrderChanged (CQRS) | 🟢 |
| MQTT → gateway | event (sub) | WS bridge → browser | 🟢 |

## Nguyên tắc
- **Đồng bộ** (liền): REST client↔gateway, gateway↔identity auth, mọi gRPC.
- **Bất đồng bộ** (nét đứt): MQTT outbox/inbox.
- Không có vòng lặp; saga order→inventory/finance là cây 1 chiều + compensation.

← [System Overview](system-overview.md) · [Request Flows](request-flows.md) · [Events](../api/events.md)
