# gRPC API — SmartFarm Platform

> Verified from `libs/smartfarm-proto` (12 `.proto`) + server impls + gateway/saga callers @ HEAD (updated 2026-10-07, GW-01 health caller).
> 9 proto có service, 3 message-only (`common`, `events`, `telemetry`). Legend: 🟢 impl+caller · 🟡 impl, no caller · 🔴 proto only, no impl.

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
| **FarmOrderService** | PlaceOrder,GetOrder,ListOrders,CancelOrder,CreateDraftOrder,UpdateDraftOrder,DeleteDraftOrder,SubmitDraftOrder | ✅ | gateway OrderRestController + GraphQL | 🟢 |
| **OrderSagaAdministrationService** | GetOrderSaga,RetryOrderSagaStep,ResumeOrderSaga,ForceCompensate/Cancel/CompleteOrder,MarkOrderSagaResolved | ✅ | gateway saga admin REST+GraphQL | 🟢 |
| **InventoryService** | CreateItem,ReceiveStock,IssueStock,ReserveStock,ReleaseReservation,CommitReservation,GetStockBalance,ListLowStock | ✅ | order saga (reserve/commit/release) + dev gateway | 🟢 |
| | **AdjustStock, TraceLot** | 🔴 | — | 🔴 impl missing |
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

## Tổng kết (verified)
- **95 RPC** định nghĩa (9 service + 3 message-only proto).
- **82 implemented** server-side; **13 missing** (5 FarmDirectory + 2 inventory + 6 health).
- **~54 RPC** có ít nhất 1 caller; **~28 implemented RPC không có caller**.
- **5 cross-service saga gRPC call**: order→inventory (3), order→finance (2).
- **Chỉ 1 RPC** đủ proto+impl+caller+test (`GetPrincipal`); chỉ 4 RPC có gRPC-layer test.

## Port map (verified from source @ 2026-10-07)

Each service binds its gRPC server to the port below (`spring.grpc.server.port`, or
`spring.grpc.server.port` nested); the gateway dials these via `smartfarm.<svc>.grpc-port`
(env-overridable `*_GRPC_PORT`). HTTP ports shown for reference. All bind `127.0.0.1` by default —
the gateway (:8080) is the sole public ingress.

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

> Historical note: the gateway briefly had `finance.grpc-port=9097` (health's port) — corrected to
> `9094` (part 4, 2026-10-07). The table above is the current, verified routing.

## Service-token flow (`POST /internal/service-token`)

Inter-service gRPC calls carry a short-lived, per-audience service JWT, not the end-user token. The
mint flow:

1. A caller (the gateway's `ServiceTokenClient`, or any client whitelisted under
   `smartfarm.identity.service-token.clients`) POSTs to identity's
   **`POST /internal/service-token`** with a JSON body
   `{clientId, secret, audience, tenantId, actorId}`.
2. Authentication is the **client secret in the body**, NOT a Bearer token — so the path is
   `permitAll` in the resource-server chain (`ServletResourceSecurity` + `IdentityConfiguration`).
   `InternalServiceTokenController` → `ServiceTokenService.issue(...)` mints a JWT scoped to the
   requested `audience` and returns a **`Grant` `{accessToken, tokenType, expiresInSeconds, audience}`**
   with HTTP **200**. Bad secret / disallowed audience → **401** (does not leak which); malformed →
   **400**.
3. The caller caches the token per `(audience, tenantId)` until ~75% of its lifetime, then attaches
   it to the downstream gRPC call via `BearerCallCredentials`. The receiving service's
   `JwtServerInterceptor` authenticates it. (Per-service **audience enforcement** on the gRPC side is
   not yet on — see `../audit/outstanding-issues.md` ISSUE-11.)

> This endpoint previously **did not exist**: the gateway POSTed it, no handler matched, Spring
> forwarded to `/error`, and the resource-server re-challenged with a misleading `401` — the root
> cause of the gateway's `UNAUTHENTICATED: Unable to apply gRPC credentials`. The controller now
> exists and the flow returns 200 end to end.

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
