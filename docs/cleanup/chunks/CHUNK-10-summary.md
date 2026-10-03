# CHUNK-10 — platform (readiness-service + farm-simulator)

**Scope:** platform readiness aggregator and a device/event simulator.

**Graph manifest (graphslice --summary):**
- readiness: 31 nodes · inbound 0 / outbound 76 · {Service:5, Configuration:4, GrpcService:3}
- simulator: 12 nodes · inbound 0 / outbound 30 · {} (no annotations counted)

**readiness-service — 5-line summary**
1. Entry points: gRPC `ReadinessGrpcService.GetPlatformReadiness`; REST `GET /api/v1/platform/readiness`; scheduled `ReadinessMonitor.refresh()`.
2. Behaviour: polls a bounded, validated target list (1..20, 1ms..5s timeout, HTTPS-or-loopback, redirects off); caches an AtomicReference<Snapshot>; NEVER probes caller-supplied URLs (SSRF-safe); NOT_READY when snapshot older than maxAge.
3. Auth: gRPC `SCOPE_platform:read` + tenant check; HTTP STATELESS JWT, readiness gated on scope, actuator permitted, else denyAll; startup refuses allowLocalHttp outside dev&!prod.
4. Config: prod wires 4 env-driven `*_HEALTH_URL` targets; allow-local-http=false. Tests: 0.
5. Verdict: PRODUCTION_IMPLEMENTATION — small but complete and defensively written; RISK: no unit tests.

**farm-simulator — 5-line summary**
1. Entry points: `SimulationController POST /api/v1/simulations/{type}`; `TaskEventObserver` MQTT subscriber. (Graph: 0 annotations.)
2. `SimulationController` returns a hard-coded 202 JSON stub and publishes NOTHING ("replace body with MqttEventPublisher in the next increment").
3. `TaskEventObserver` subscribes `smartfarm/+/+/domain/task-changed/v1` and only LOGS; its own javadoc: "not a durable inbox or business consumer"; does NOT verify the HMAC envelope.
4. Config RISK: `spring.main.web-application-type: none` disables the web layer, so the REST emit path is dead as configured; no security/persistence/tests.
5. Verdict: DEMO_OR_SKELETON — a scaffold (stub emit + log-only observer, web disabled). Finish it (real MqttClientFactory publisher + enable web) or quarantine as explicitly dev-only.
