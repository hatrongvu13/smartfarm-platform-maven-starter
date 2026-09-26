# smartfarm-gateway: security integration (Java 17)

This archive replaces **only** `apps/smartfarm-gateway` in the Maven workspace. Requires the previously supplied
`libs/smartfarm-security` module version `0.1.0-SNAPSHOT`. The root aggregator and other services remain unchanged.

## What changes

- Import `ReactiveResourceSecurity` from common security and enable reactive method security.
- Require HTTPS issuer/JWKS and audience on startup; no hardcoded secrets or default insecure profile.
- WebFlux Resource Server authenticates REST and GraphQL via JWT; public endpoints: Actuator health/liveness/readiness
  only.
- `GET /api/v1/me` and GraphQL `platformStatus` require `SCOPE_farm:read`. Tenant and subject are read from verified
  JWT, never from client headers.
- No token minting and no implicit user-token forwarding to gRPC. Gateway gRPC client starter is present for future BFF
  calls.

## Migration from old Gateway

1. Replace `apps/smartfarm-gateway` with the folder in this ZIP; **delete** old `config/DevSecurityConfiguration.java`
   and any duplicate `SecurityWebFilterChain` beans. Do not retain the old GraphQL resolver/schema; this ZIP includes
   replacements.
2. Ensure `libs/smartfarm-security` is installed/available; root `mvn -pl apps/smartfarm-gateway -am clean verify`
   builds its dependency from source.
3. Supply an HTTPS JWKS endpoint that serves public RSA keys and issues RS256 access tokens with `iss`,
   `aud=smartfarm-gateway`, `exp`, `sub`, `tenant_id`, and `scope` containing `farm:read`.
4. Export configuration and start:

```bash
export SMARTFARM_JWT_ISSUER=https://auth.example.com
export SMARTFARM_JWKS_URI=https://auth.example.com/.well-known/jwks.json
export SMARTFARM_JWT_AUDIENCE=smartfarm-gateway
mvn -pl apps/smartfarm-gateway -am clean install
mvn -f apps/smartfarm-gateway/pom.xml spring-boot:run
```

The first build is a Maven reactor build. `spring-boot:run` uses locally installed common artifacts; do not use `-am`
with the run goal.

## Smoke tests

```bash
curl -i http://localhost:8080/actuator/health                 # 200
curl -i http://localhost:8080/api/v1/me                       # 401
curl -i -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/api/v1/me
curl -i http://localhost:8080/graphql -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' -d '{"query":"{ platformStatus { name status tenantId } }"}'
```

A valid JWT without `farm:read` gets HTTP 403 on `/api/v1/me`. GraphQL field authorization errors can be returned in the
GraphQL `errors` array with HTTP 200; do not use HTTP status alone to judge GraphQL field authorization.

The integration test substitutes a **test-only** decoder to cover public health, 401, 403, verified tenant and GraphQL.
It does **not** prove real JWKS signature verification. Add an IdP/JWKS integration test before production.

## Security boundary

The common security library rejects missing issuer/JWKS and non-HTTPS URLs. Do not add a `permitAll` dev configuration.
A local dev IdP should expose HTTPS; if that is not ready, run only automated tests with the test decoder. gRPC
service-to-service auth is not activated here: each downstream service must independently verify its own audience and
token. Never forward a gateway-audience user token to a service with a different audience. TLS/mTLS on gRPC transport
remains separate work.

The Gateway is a WebFlux BFF, not an authorization server. Actual GraphQL/gRPC federation and token exchange/service
credentials are subsequent implementation steps.
## LiveStock example
```bash
curl -X POST http://localhost:8080/api/v1/livestock/tasks \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Idempotency-Key: task-demo-001" \
  -H "Content-Type: application/json" \
  -d '{
    "farmId": "farm-demo",
    "title": "Cho ăn buổi sáng",
    "assigneeId": "worker-1"
  }'
  
curl http://localhost:8080/api/v1/livestock/tasks/<taskId> \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```


