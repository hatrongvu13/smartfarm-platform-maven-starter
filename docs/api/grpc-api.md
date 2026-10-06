# gRPC API — SmartFarm Platform

> Verified from `libs/smartfarm-proto` (12 `.proto`) + server impls + gateway/saga callers @ HEAD `eaaa112`.
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
| **AnimalHealthService** | RecordObservation,ListObservations,RecordVaccination,ListVaccinations | ✅ | 🔴 **no gateway caller** (GW-01 orphan) | 🟡 |
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

← [System Overview](../architecture/system-overview.md) · [events.md](events.md) · [rest-api.md](rest-api.md)
