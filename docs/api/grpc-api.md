# gRPC API — SmartFarm Platform

> Reconciled 2026-10-15 from `libs/smartfarm-proto/src/main/proto` and current server implementations. There are 14 declared gRPC services and 127 RPC methods; 116 have server handlers, while 11 are not implemented (5 `FarmDirectoryService`, 6 `AnimalHealthService`). Having a proto method or Java bean does not alone prove runtime registration, gateway caller coverage, or production security.

## Traceability (impl vs caller vs test)
| Proto service | RPC | Impl | Caller | Status |
|---------------|-----|:----:|--------|:------:|
| **IdentityDirectoryService** | GetPrincipal | ✅ | gateway + CheckPermission path | 🟢 (only RPC with proto+impl+caller+**test**) |
| | UpdatePrincipalProfile | ✅ | gateway GraphQL `updateMyProfile` | 🟢 |
| | CheckPermission / BatchCheckPermissions | ✅ | gateway authz | 🟢 |
| | Ping | ✅ | — | 🟡 |
| **IdentityAdministrationService** | ListRoles,GetRole,ListPermissions,ListUsers,GetUserAuthorization,CreateUser,AssignRole,RevokeRole,Disable/Enable/SuspendMembership | ✅ | 7 có caller GraphQL identity admin; phần còn lại 🟡 | 🟢/🟡 |
| **PlatformAuthorizationAdministrationService** | ListPlatformRoles,CreatePlatformRole,Grant/RevokePlatformPermission | ✅ | 🟡 no gateway caller (4) | 🟡 |
| **IdentityCredentialService** | GetSecurityProfile,BeginTotpEnrollment,ConfirmTotpEnrollment,DisableOwnMfa,RegenerateRecoveryCodes,ResetUserMfa,ChangeOwnPassword | ✅ | gateway GraphQL MFA mutations | 🟢 |
| **IdentityTokenService** | IssueServiceToken,ExchangeDelegatedToken,ListSessions,RevokeSession,RevokeAllSessions | ✅ | no current gateway caller documented | 🟡 handlers exist; issue/exchange require an authenticated caller JWT, session methods require authorities; no dedicated server-level registration/policy test |
| **FarmOrderService** | PlaceOrder,GetOrder,ListOrders,CancelOrder,CreateDraftOrder,UpdateDraftOrder,DeleteDraftOrder,SubmitDraftOrder | ✅ | gateway OrderRestController + GraphQL | 🟢 |
| **OrderSagaAdministrationService** | GetOrderSaga,RetryOrderSagaStep,ResumeOrderSaga,ForceCompensate/Cancel/CompleteOrder,MarkOrderSagaResolved | ✅ | gateway saga admin REST+GraphQL | 🟢 |
| **InventoryService** | All 20 proto methods, including AdjustStock, TraceLot, item and warehouse CRUD/status | ✅ | order saga + dev gateway | 🟢/🟡 caller coverage varies |
| **FarmFinanceService** | RecordExpense,ReverseExpense | ✅ | order saga | 🟢 |
| | RecordIncome,GetTransaction,ListTransactions,RecordPayable,RecordReceivable,SettleDebt,GetBatchCost,GetCashFlow | ✅ | 🟡 no caller (hoặc chỉ dev GraphQL batchCost/cashFlow) | 🟡 |
| **LivestockTaskService** | CreateTask,GetTask,ListTasks,AssignTask,AcceptTask,CompleteTask,CancelTask,CreateSchedule,ListSchedules,RegisterAnimal,GetAnimal,ListAnimals | ✅ | gateway dev controllers | 🟡 dev-only caller |
| | Ping | ✅ | — | 🟡 |
| **AnimalHealthService** | RecordObservation,ListObservations,RecordVaccination,ListVaccinations | ✅ | gateway dev REST facade `/api/v1/health/*` (GW-01 fixed) | 🟡 dev-only caller |
| | ScheduleExamination,CompleteExamination,RecordTreatment,ListTreatments,ListAlerts,AcknowledgeAlert | 🔴 | — | 🔴 impl missing (6) |
| **ReportingService** | (xem reporting.proto) | ✅ | gateway dev controller | 🟡 dev-only |
| **PlatformReadinessService** | (readiness.proto) | ✅ | 🟡 1 RPC no caller | 🟡 |
| **FarmDirectoryService** (farm.proto) | all 5 | 🔴 | — | 🔴 entirely unimplemented (PLANNED) |
| telemetry.proto | — | — | — | message-only (PLANNED V3) |

## Tổng kết (verified from current source)
- **127 RPC** across 14 declared services; `common`, `events`, and `telemetry` are message-only proto files.
- **116 server handlers** exist. Missing implementations are five `FarmDirectoryService` methods and six `AnimalHealthService` methods.
- The older inventory claim that `AdjustStock` and `TraceLot` are missing is stale: both have handlers in `InventoryGrpcService`.
- Caller and test coverage is not inferred from implementation; follow the service-specific source/test links before relying on an operation.
- **5 cross-service saga gRPC calls** are visible in source: order→inventory (3) and order→finance (2).

## Port map (current configured ports)

The gateway dials the configured ports via `smartfarm.<svc>.grpc-port`
(env-overridable `*_GRPC_PORT`). HTTP ports are shown for reference. Bind addresses are
profile-specific; inspect the active service configuration before assuming network isolation.
Identity production explicitly defaults to loopback. Identity dev config sets its gRPC port but
does not set an explicit gRPC bind address.

| Service   | gRPC port | HTTP port | Gateway property      |
|-----------|:---------:|:---------:|-----------------------|
| gateway   | —         | 8080      | (edge ingress)        |
| identity  | **9092**  | 8092      | `smartfarm.identity.grpc-port`  |
| livestock | **9091**  | 8081      | `smartfarm.livestock.grpc-port` |
| inventory | **9093**  | 8083      | `smartfarm.inventory.grpc-port` |
| finance   | **9094**  | 8084      | `smartfarm.finance.grpc-port`   |
| order     | **9095**  | 8085      | `smartfarm.order.grpc-port`     |
| reporting | **9096**  | 8086      | `smartfarm.reporting.grpc-port` |
| health    | **9097**  | 8087      | `smartfarm.health.grpc-port`    |

> Recheck gateway routing properties whenever service ports or profiles change.

## Service-token issuance and security

Identity contains two issuance transports in the current source:

1. HTTP `POST /internal/service-token`, which requires the dedicated `X-Identity-Internal-Secret` header as well as client-secret validation in `ServiceTokenService`.
2. gRPC `IdentityTokenService.IssueServiceToken` and `ExchangeDelegatedToken`, implemented by `IdentityTokenGrpcService`; both require an authenticated caller JWT and configured client credentials.

gRPC TLS remains disabled by default. Identity production defaults its gRPC bind to loopback; dev does not explicitly set the gRPC bind address. The HTTP issuance route remains a duplicate path; its shared header is an application authentication check, not transport encryption. Session-management RPCs require session authorities. Unit policy coverage exists; dedicated server-level registration and enforcement tests remain necessary before production use.

### HTTP service-token flow (`POST /internal/service-token`)

Inter-service gRPC calls carry a short-lived, per-audience service JWT, not the end-user token. The
mint flow:

1. A caller (the gateway's `ServiceTokenClient`, or any client whitelisted under
   `smartfarm.identity.service-token.clients`) POSTs to identity's
   **`POST /internal/service-token`** with a JSON body
   `{clientId, secret, audience, tenantId, actorId}`.
2. Authentication is the **client secret in the body**, NOT a Bearer token — so the path is
   The bearer-token filter permits the route, but the controller first validates the dedicated
   `X-Identity-Internal-Secret` header before checking these client credentials.
   `InternalServiceTokenController` → `ServiceTokenService.issue(...)` mints a JWT scoped to the
   requested `audience` and returns a **`Grant` `{accessToken, tokenType, expiresInSeconds, audience}`**
   with HTTP **200**. Bad secret / disallowed audience → **401** (does not leak which); malformed →
   **400**.
3. The caller caches the token per `(audience, tenantId)` until ~75% of its lifetime, then attaches
   it to the downstream gRPC call via `BearerCallCredentials`. The receiving service's
   `JwtServerInterceptor` authenticates it. Token audience and authorization enforcement must also
   be checked against the receiving service's current configuration and tests.

The HTTP endpoint currently exists in source, but remains a duplicate machine-token issuance path.
The dedicated shared header is an application authentication check, not transport encryption; use
TLS/mTLS and keep the listener private. See [Identity service](../services/identity.md) and
[Security architecture](../security/security-architecture.md).

## Standardized error codes (gRPC → HTTP, since 2026-10-07)

Downstream gRPC `Status.Code` is translated to the edge HTTP status and a stable client-facing code
by the **single shared mapping** `libs/smartfarm-security/.../grpc/GrpcStatusHttpMapping` — used by
`GatewayGrpcExceptionMapper` (REST + GraphQL) and every gateway REST facade, so the same downstream
failure surfaces identically everywhere.

| gRPC `Status.Code`            | HTTP status | Stable code             |
|-------------------------------|:-----------:|-------------------------|
| INVALID_ARGUMENT, OUT_OF_RANGE | 400         | `BAD_REQUEST`           |
| UNAUTHENTICATED               | 401         | `UNAUTHENTICATED`       |
| PERMISSION_DENIED             | 403         | `FORBIDDEN`             |
| NOT_FOUND                     | 404         | `NOT_FOUND`             |
| ALREADY_EXISTS                | 409         | `CONFLICT`              |
| FAILED_PRECONDITION           | 409         | `FAILED_PRECONDITION`   |
| ABORTED                       | 409         | `VERSION_CONFLICT`      |
| RESOURCE_EXHAUSTED            | 429         | `RATE_LIMITED`          |
| DEADLINE_EXCEEDED             | 504         | `TIMEOUT`               |
| UNAVAILABLE, UNIMPLEMENTED    | 502         | `DOWNSTREAM_UNAVAILABLE`|
| (anything else / null)        | 500         | `INTERNAL`              |

> Three gRPC codes share HTTP 409 but keep distinct stable codes so clients can tell a duplicate
> (`CONFLICT`) from a precondition failure (`FAILED_PRECONDITION`) from an optimistic-lock conflict
> (`VERSION_CONFLICT`). Before this was common-ised, each gateway facade kept its own drifted copy
> (some returned 502 for an unknown code, some 500; some omitted `ALREADY_EXISTS`/`ABORTED`).

← [System Overview](../architecture/system-overview.md) · [events.md](events.md) · [rest-api.md](rest-api.md)
