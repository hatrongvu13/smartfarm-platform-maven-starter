# CHUNK-01 — libs (common-kernel / security / messaging / proto)

**Scope:** `libs/smartfarm-common-kernel`, `libs/smartfarm-security`,
`libs/smartfarm-messaging`, `libs/smartfarm-proto`.

**Graph manifest (graphslice --summary; graph dated 2026-10-02 08:56):**
- common-kernel: 87 nodes · inbound 143 / outbound 36 · ann {Entity:1}
- security: 196 nodes · inbound 131 / outbound 333 · ann {Configuration:7, Service:5}
- messaging: 0 nodes (module postdates the graph) · classified from source
- proto: 1 node · inbound 11 / outbound 0 (generated stubs)

**5-line summary**
1. common-kernel: shared immutable primitives (DomainEvent, OutboxStatus, PageQuery/Result, BusinessException, RequestMetadata); 7 unit tests; most-consumed lib (143 inbound). PRODUCTION_IMPLEMENTATION.
2. security: JWT issuer/verifier/validator, gRPC JwtServerInterceptor + GrpcMethodPolicy, servlet/reactive resource security, MqttSecurityVerifier (HMAC-SHA256, topic-bound, constant-time, default-off) wired via META-INF auto-config; 5 tests. PRODUCTION_IMPLEMENTATION (home of ISSUE-01/02).
3. messaging: shared DispatchFailureClassifier taxonomy + MqttClientFactory (single Paho construction, fail-fast FILE mode) + MqttConsumerSettings guards; consumed by order/identity/inventory; 2 tests. PRODUCTION_IMPLEMENTATION (ISSUE-04/05/09).
4. proto: generated protobuf/gRPC contract types for every service; low node count is a generated-source artifact. PRODUCTION_IMPLEMENTATION (generated).
5. No TODO/stub/dead code in any lib source; "orphan" security classes are Spring auto-configured, not dead.
