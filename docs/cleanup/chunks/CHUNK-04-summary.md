# CHUNK-04 — apps/smartfarm-gateway

**Scope:** edge/aggregation tier (REST + GraphQL + MQTT event fan-out).

**Graph manifest (graphslice --summary):**
- 355 nodes · inbound 0 / outbound 927
- annotations: {Configuration:4, Service:3, RestController:3}

**5-line summary**
1. Entry points: REST (WhoAmIController, AuthProxyController, OrderRestController); GraphQL controllers (identity/farm/order/order-saga, @QueryMapping/@MutationMapping); MQTT MqttEventSubscriber for event fan-out.
2. Dev facades: LivestockDevController, ReportingDevController, InventoryDevSetupController — all @Profile("dev & !prod"); real @PreAuthorize + gRPC→HTTP status mapping, NOT demo stubs.
3. Persistence: none (stateless edge; 927 outbound cross-module edges, 0 inbound).
4. Auth: uniform @PreAuthorize SCOPE_* on every mapping; per-service tokens via ServiceTokenClient + BearerCallCredentials; GatewayRestExceptionHandler.
5. Verdict: PRODUCTION_IMPLEMENTATION. RISK: confirm the `dev` Spring profile is never active in prod (dev facades are the only thing gating would expose).
