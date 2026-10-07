# Service Communications — SmartFarm Platform

> Verified @ HEAD (updated 2026-10-07). Ma tran giao tiep thuc te (co loi goi trong source).

## Ma tran (from -> to)
| From → To | Protocol | Chi tiết | Status |
|-----------|----------|----------|:------:|
| client → gateway | REST + GraphQL + WS | edge ingress :8080 | 🟢 |
| gateway → identity | REST (auth) + gRPC | auth proxy + directory/admin/credential | 🟢 |
| gateway → order | gRPC | order + saga admin | 🟢 |
| gateway → inventory | gRPC | dev-only facade | 🟡 |
| gateway → reporting | gRPC | dev-only facade | 🟡 |
| gateway → finance | gRPC | 2 dev-only GraphQL | 🟡 |
| gateway → livestock | gRPC | dev-only facade | 🟡 |
| gateway → health | gRPC | dev REST facade `/api/v1/health/*` (GW-01 fixed) | 🟡 |
| order → inventory | gRPC | ReserveStock, CommitReservation, ReleaseReservation (saga) | 🟢 |
| order → finance | gRPC | RecordExpense, ReverseExpense (saga) | 🟢 |
| order → MQTT | event (outbox) | OrderChanged protobuf | 🟢 |
| livestock → MQTT | event (outbox) | task-* protobuf | 🟢 |
| health → MQTT | event (outbox) | health-* protobuf (all profiles, signed — EVT-03 fixed) | 🟢 |
| identity → MQTT | event (outbox) | protobuf `IdentityLifecycleEvent` (EVT-01 fixed) | 🟢 |
| MQTT → inventory | event (sub) | task-changed/v1 only | 🟡 |
| MQTT → order | event (sub) | OrderChanged (CQRS) | 🟢 |
| MQTT → identity | command (inbox) | admin commands, feature-flagged OFF by default | 🟡 |
| MQTT → gateway | event (sub) | WS bridge → browser | 🟢 |

## Nguyên tắc
- **Đồng bộ** (liền): REST client↔gateway, gateway↔identity auth, mọi gRPC.
- **Bất đồng bộ** (nét đứt): MQTT outbox/inbox.
- Không có vòng lặp; saga order→inventory/finance là cây 1 chiều + compensation.

## Inbound MQTT commands + command-ack (identity)

`IdentityMqttCommandDispatcher` nhận **lệnh admin bất đồng bộ** qua MQTT inbox (role assign/revoke,
membership enable/suspend/disable), gated bởi `smartfarm.identity.mqtt.commands.enabled` (mặc định
**OFF**). Sau khi xử lý mỗi lệnh, nó phát một event kết quả:

- **Event:** `identity.command.result`
- **Topic:** `smartfarm/<tenant>/_global/domain/command.result/v1`
- **Payload:** protobuf `DomainEvent` → `IdentityLifecycleEvent` (sau EVT-01), `data` =
  `{commandId, commandType, status: PROCESSED|FAILED, errorCode?}`
- **Consumer:** **không có consumer backend** — đây là **command-ack hướng WS**. Topic khớp filter WS
  bridge của gateway (`smartfarm/+/+/domain/#`), nên result được đẩy tới **client FE** đã phát lệnh;
  FE đối chiếu qua `commandId`/`correlationId`. Đây là mẫu async command → result-event → WS push,
  **không phải orphan/bug** (ISSUE-06 reclassified **by-design**).

← [System Overview](system-overview.md) · [Request Flows](request-flows.md) · [Events](../api/events.md)
