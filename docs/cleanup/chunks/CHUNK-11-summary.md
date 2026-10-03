# CHUNK-11 — cross-service gRPC

**Scope:** proto-defined services and the service-to-service call graph, confirmed by
reading source (the dependency graph does NOT capture gRPC stub edges — stubs live in
`libs/smartfarm-proto`). No `@GrpcClient` annotations exist; all channels are
hand-wired beans.

**Services defined in proto (12 service blocks across 11 proto files):**
FarmDirectoryService (farm), FarmFinanceService (finance), AnimalHealthService
(health), IdentityDirectory/Administration/PlatformAuthorizationAdministration/Credential
(identity ×4), InventoryService (inventory), LivestockTaskService (livestock),
FarmOrderService + OrderSagaAdministrationService (order ×2), PlatformReadinessService
(readiness), ReportingService (reporting).

**Server impls present:** finance, health, identity (4), inventory, livestock,
order (2), readiness, reporting. **MISSING: `FarmDirectoryService` has no
`*ServiceImplBase` subclass anywhere** → dead/unbuilt contract (RISK).

**Caller → callee edges (from source):**
- gateway → identity (Directory/Admin/Credential/PlatformAuthz), order (Farm + SagaAdmin), inventory, finance — all profiles.
- gateway → livestock, reporting — ONLY via `@Profile("dev & !prod")` dev controllers.
- order → inventory (reserve/commit/release) AND order → finance (record/reverse expense) — saga forward + compensation executors. The only true service orchestration.
- reporting → livestock (ListTasks, read-only data pull for exports).

**Notes:** reporting's client allows plaintext on loopback only; order/gateway use
hardened channel factories. See `04-gateway-contract-coverage.md` for the gateway's
exposed edge surface (REST/GraphQL/WS) and which service contracts it fronts.

**Verdict:** cross-service gRPC is minimal and intentional — two real paths (order
saga, reporting read). RISK items: unimplemented FarmDirectoryService; reporting/
livestock/inventory dev-only gateway facades must stay out of prod.
