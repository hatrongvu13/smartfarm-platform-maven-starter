# System Overview — SmartFarm Platform

> Verified from source @ HEAD `f113657` (updated 2026-10-07). Màu: 🟢 verified-implemented · 🟡 partial · 🔴 missing/not-mapped · ⬜ planned.
> Mỗi service node liên kết tới `docs/services/<name>.md`.

## 1. Kiến trúc tổng thể

Monorepo Maven 14 module. **Edge** = `smartfarm-gateway` (Spring WebFlux reactive, :8080): nhận REST + GraphQL từ client, dịch sang **gRPC** gọi các service nội bộ. Giao tiếp sự kiện qua **MQTT** (Mosquitto :1883) theo outbox/inbox. Mỗi service có Postgres riêng (TEST/PROD) hoặc dùng chung `smartfarm` (dev).

- **Đồng bộ**: REST (client↔gateway, gateway↔identity auth), gRPC (gateway↔services, order↔inventory/finance).
- **Bất đồng bộ**: MQTT domain events (outbox relay → broker → consumer/WS bridge).

## 2. System context

```mermaid
flowchart TB
  client["Client (web/CLI)"]:::ok
  gw["smartfarm-gateway :8080<br/>REST + GraphQL edge"]:::ok
  broker(["MQTT Mosquitto :1883"]):::partial
  subgraph services[Backend services - gRPC]
    id["identity :8092/9092"]:::ok
    ord["order :8085/9095"]:::ok
    inv["inventory :8083/9093"]:::ok
    fin["finance :8084/9094"]:::partial
    liv["livestock :8081/9091"]:::ok
    rep["reporting :8086/9096"]:::partial
    hea["health :8087/9097"]:::ok
  end
  pg[("Postgres 17")]:::ok
  redis[("Redis")]:::ok
  client -->|REST/GraphQL| gw
  gw -->|gRPC| id
  gw -->|gRPC| ord
  gw -->|gRPC dev| inv
  gw -->|gRPC dev| liv
  gw -->|gRPC dev| rep
  gw -->|gRPC dev| fin
  gw -->|gRPC dev| hea
  gw -. "MQTT sub (WS bridge)" .-> broker
  id & ord & inv & fin & liv & rep & hea --> pg
  id --> redis
  ord -. outbox .-> broker
  liv -. outbox .-> broker
  hea -. "outbox (protobuf)" .-> broker
  id -. "outbox (protobuf)" .-> broker
  broker -. sub .-> inv
  broker -. "sub (CQRS)" .-> ord
  classDef ok fill:#1b5e20,color:#fff,stroke:#2e7d32;
  classDef partial fill:#8d6e00,color:#fff,stroke:#f9a825;
  classDef missing fill:#8e1b1b,color:#fff,stroke:#c62828;
  classDef planned fill:#424242,color:#fff,stroke:#757575;
```

🟢 **health-service** giờ đã nối vào gateway: dev REST facade `/api/v1/health/*` (observations/vaccinations/alerts) qua gRPC client `AnimalHealthService` (GW-01 fixed). 🟡 **finance** chỉ lộ 2 GraphQL dev-only.

## 3. Service dependency graph

```mermaid
flowchart LR
  gw["gateway"]:::ok
  id["identity"]:::ok
  ord["order"]:::ok
  inv["inventory"]:::ok
  fin["finance"]:::partial
  gw -->|"gRPC: auth/directory/admin"| id
  gw -->|"gRPC: order/saga"| ord
  gw -->|"gRPC dev"| inv
  ord ==>|"gRPC: reserveStock / commitReservation / releaseReservation"| inv
  ord ==>|"gRPC: recordExpense / reverseExpense"| fin
  classDef ok fill:#1b5e20,color:#fff;
  classDef partial fill:#8d6e00,color:#fff;
```

Dependency xuyên service **thực tế có trong source**: `order → inventory` (3 RPC saga), `order → finance` (2 RPC saga). Không có `identity → order`. Livestock/reporting/health **không** là downstream của service khác (chỉ nhận gRPC từ gateway và/hoặc publish event).

## 4. Gateway routing overview

```mermaid
flowchart LR
  c["Client"]:::ok
  subgraph gw["gateway :8080"]
    auth["/api/v1/auth/* (REST proxy)"]:::ok
    me["/me (REST)"]:::ok
    ords["/api/v1/orders/* (REST->gRPC)"]:::ok
    saga["/api/v1/order-sagas/* (REST->gRPC, admin)"]:::ok
    gql["/graphql (Query+Mutation)"]:::ok
    livd["/api/v1/livestock/* (dev only)"]:::partial
    invd["/api/v1/inventory/* (dev only)"]:::partial
    repd["/api/v1/reports/* (dev only)"]:::partial
    head["/api/v1/health/* (dev only)"]:::partial
    ws["/ws/** (events WS)"]:::ok
  end
  c --> auth & me & ords & saga & gql & livd & invd & repd & head & ws
  auth -->|"REST (WebClient)"| id["identity"]:::ok
  me --> id
  ords & saga -->|gRPC| ord["order"]:::ok
  gql -->|gRPC| id & ord & inv["inventory"]:::ok & fin["finance"]:::partial
  livd -->|gRPC| liv["livestock"]:::ok
  invd -->|gRPC| inv
  repd -->|gRPC| rep["reporting"]:::partial
  head -->|gRPC| hea["health"]:::partial
  classDef ok fill:#1b5e20,color:#fff;
  classDef partial fill:#8d6e00,color:#fff;
```

Chi tiết từng route: [`docs/architecture/gateway-mapping.md`](./gateway-mapping.md).

## 5. Authentication flow (login)

```mermaid
sequenceDiagram
  participant C as Client
  participant GW as Gateway
  participant ID as identity
  C->>GW: POST /api/v1/auth/login {email, password}
  GW->>ID: REST proxy (WebClient) /api/v1/auth/login
  ID->>ID: verify credentials, lockout check, resolve tenant from email
  alt MFA enrolled (forced for SUPERADMIN)
    ID-->>GW: MFA_REQUIRED (challengeToken)
    C->>GW: POST /api/v1/auth/mfa/verify {challengeToken, code}
    GW->>ID: REST proxy /api/v1/auth/mfa/verify
  end
  ID-->>GW: accessToken RS256 15m + refreshToken rotating
  GW-->>C: tokens
  Note over C,GW: Bearer access on later calls gateway validates JWT issuer aud JWKS
```

🟢 Verified @ `f113657`: login nhận **email + password** (bỏ `tenantId` bắt buộc; nhiều tenant → `TENANT_SELECTION_REQUIRED`). `AuthProxyController` (gateway) proxy sang `AuthController` (identity) qua WebClient; JWT RS256 15 phút + JWKS + audience/tenant validation (`libs/smartfarm-security`). SUPERADMIN bị ép TOTP → login trả `MFA_REQUIRED` trước khi cấp token.

## 6. Registration flow

```mermaid
sequenceDiagram
  participant C as Client
  participant GW as Gateway (/api/v1/auth/register, permitAll)
  participant ID as identity
  participant PG as Postgres (identity)
  C->>GW: POST /api/v1/auth/register
  GW->>ID: REST proxy /api/v1/auth/register
  ID->>PG: create account + membership (RBAC default role)
  ID->>ID: enqueue identity domain event (protobuf outbox)
  ID-->>GW: 201 principal
  GW-->>C: created
```

🟢 identity publish event dạng **protobuf `DomainEvent`** (payload `IdentityLifecycleEvent`), cùng format mọi service khác → event identity tới được WS bridge của gateway (EVT-01/02 đã fix, xem §8).

## 7. Request flow xuyên service — PlaceOrder saga (REST → gRPC → saga → inventory + finance)

```mermaid
sequenceDiagram
  participant C as Client
  participant GW as Gateway (/api/v1/orders)
  participant ORD as order (gRPC FarmOrderGrpcService)
  participant SAGA as order saga worker (persistent)
  participant INV as inventory (gRPC)
  participant FIN as finance (gRPC)
  participant OB as order outbox -> MQTT
  C->>GW: POST /api/v1/orders (Idempotency-Key, SCOPE_orders:write)
  GW->>ORD: gRPC PlaceOrder(context, farmId, lines)
  ORD->>ORD: persist order + saga (CREATED)
  ORD-->>GW: Order(view)
  GW-->>C: 200 Order
  Note over SAGA: async, persistent saga worker
  SAGA->>INV: reserveStock -> commitReservation
  SAGA->>FIN: recordExpense
  alt failure
    SAGA->>INV: releaseReservation
    SAGA->>FIN: reverseExpense
  end
  SAGA->>OB: OrderChanged (outbox)
  OB-->>GW: MQTT domain event -> WS bridge -> browser
```

🟢 Verified: 5 cross-service gRPC saga calls tồn tại trong source; outbox at-least-once + CQRS projection; failure path = compensation (release/reverse).

## 8. Event communication overview

```mermaid
flowchart LR
  subgraph pub[Publishers - outbox relay]
    ord["order protobuf"]:::ok
    liv["livestock protobuf"]:::ok
    hea["health protobuf (all profiles, signed)"]:::ok
    id["identity protobuf (lifecycle)"]:::ok
  end
  broker(["MQTT smartfarm/{tenant}/{farm}/domain/.../v1"]):::partial
  subgraph sub[Consumers]
    inv["inventory (task-changed/v1 only)"]:::partial
    ordc["order CQRS (OrderChanged)"]:::ok
    wsb["gateway WS bridge -> browser"]:::ok
  end
  ord & liv & hea & id -->|protobuf DomainEvent| broker
  broker --> inv & ordc & wsb
  classDef ok fill:#1b5e20,color:#fff;
  classDef partial fill:#8d6e00,color:#fff;
  classDef missing fill:#8e1b1b,color:#fff;
```

🔐 **MQTT message auth (HMAC-SHA256 signed envelope)** đã wired vào **mọi** publisher (`sign`) và consumer (`verify`) qua `libs/smartfarm-security` (`MqttSecurityVerifier` + self-describing `SFM1` frame). `dev`: bật sẵn (sign+verify ON, shared dev secret). `prod`: default OFF, bật theo rollout 2 pha (sign-all → verify-all) — chi tiết [`mqtt-acl-mtls.md`](../security/mqtt-acl-mtls.md). Broker-level ACL + mTLS vẫn là sample config (chưa enable).

**Vấn đề đã xác minh** (chi tiết [`request-flows.md`](./request-flows.md) + [`unresolved-items.md`](../audit/unresolved-items.md)):
- ✅ **EVT-01 (RESOLVED)**: identity giờ emit **protobuf `DomainEvent`** (payload `IdentityLifecycleEvent`, oneof field 24) → WS bridge parse được. Trước đây emit JSON nên bị drop.
- ✅ **EVT-02 (RESOLVED)**: identity topic chuẩn hoá về `smartfarm/{tenant}/_global/domain/{event}/v1` (bỏ segment `{aggr}-{event}`), khớp filter `smartfarm/+/+/domain/#`.
- 🟡 inventory chỉ sub `task-changed/v1`; 6 leaf lifecycle livestock khác chỉ WS bridge nhận.
- 🟢 health publisher giờ active mọi profile (gated `smartfarm.health.outbox.enabled`), ký HMAC, nhận broker cấu hình → **đã có đường event health ở prod** (EVT-03 fixed, Option A).

---

## Service docs
- [gateway](../services/gateway.md) · [identity](../services/identity.md) · [order](../services/order.md) · [inventory](../services/inventory.md) · [finance](../services/finance.md) · [livestock](../services/livestock.md) · [health](../services/health.md) · [reporting](../services/reporting.md)
- ← [Documentation Index](../index.md)
