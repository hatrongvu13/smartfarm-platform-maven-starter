# FarmVerse Identity Service | Java 17, Maven, Spring Boot 4

Self-hosted identity service with local accounts, tenant membership, MFA, RBAC, internal administration, and a partially implemented OAuth 2.0 Authorization Server/OIDC Provider. It now provides standard authorization/token, discovery, JWKS, UserInfo, and configured `client_credentials` endpoints backed by persisted OAuth state. Authorization Code + PKCE, the browser login/MFA integration, and OIDC claim mapping are implemented but not yet covered by end-to-end interoperability tests. Gateway/Web and machine/delegated callers still use legacy custom APIs; PostgreSQL V4 migration is not runtime-verified. Do not treat the protocol migration as product-ready. See [the target design and remaining gates](../../docs/architecture/identity-oauth2-oidc-target.md).

## Setup and run (dev)

Place this folder at `services/identity-service/`. Add `<module>services/identity-service</module>` to root `pom.xml`.

```bash
cd services/identity-service
./scripts/generate-dev-keys.sh
mvn clean spring-boot:run
```

The `dev` profile uses PostgreSQL (`IDENTITY_DB_URL`, defaulting to `jdbc:postgresql://localhost:5432/farmverse`) with Hibernate schema update; H2 is used by the `test` profile. Generated RSA keypair is local and ignored by Git. **Do not regenerate keys** after creating accounts unless you intentionally invalidate existing JWTs. When Identity starts against a database without a super-admin and `IDENTITY_SUPERADMIN_BOOTSTRAP_TOKEN` is unset, it generates a one-time random bootstrap key, stores only its SHA-256 hash, and prints the key once in the Identity service log. Open the Gateway welcome screen and enter that key with the first administrator's email and password. The key remains valid across service restarts until successfully used, then is consumed. An explicitly configured `IDENTITY_SUPERADMIN_BOOTSTRAP_TOKEN` continues to override this generated-key flow. Submit account credentials in the request body, not through environment properties. Privileged users complete MFA enrollment through the challenge-token-protected endpoints. `IDENTITY_BOOTSTRAP_TENANT` configures the platform authorization tenant, not the bootstrap account. Use `dev-tls` for HTTP end-to-end tests. Never expose plaintext login/token endpoints outside an isolated localhost development session.

In the `dev` profile, Identity automatically creates a random 256-bit MFA encryption key at `.local/identity-mfa-encryption-key` on first launch and reuses it on subsequent launches; the file is owner-readable/writable and ignored by Git. Set `IDENTITY_MFA_ENCRYPTION_KEY` to override it, or `IDENTITY_MFA_ENCRYPTION_KEY_FILE` to change the local file path. For non-dev deployments, provide `IDENTITY_MFA_ENCRYPTION_KEY` as a Base64-encoded 32-byte secret; startup fails if it is missing or malformed. Keep the key private and stable while encrypted MFA enrollments are in use: replacing it makes existing MFA secrets unreadable. If the previously committed development key was used for MFA enrollments, reset those enrollments before switching to a new key.

## Connect the existing Gateway

The previously supplied `farmverse-security` library requires **HTTPS** issuer/JWKS. To run Identity and Gateway together locally **without a third-party identity application or reverse proxy**, use Spring Boot's embedded HTTPS server:

```bash
cd services/identity-service
./scripts/generate-dev-keys.sh       # only once; do not overwrite existing signing keys
./scripts/generate-dev-tls.sh        # only once for the local certificate
export IDENTITY_BOOTSTRAP_TENANT=farm-demo
mvn spring-boot:run -Dspring-boot.run.profiles=dev,dev-tls
```

In the Gateway terminal, trust the **dev certificate** using a local truststore (never use `trustAll`):

```bash
mkdir -p .local
printf '\n.local/\n' >> .gitignore # add once to the ROOT workspace gitignore
keytool -importcert -noprompt -alias farmverse-identity-dev \
 -file services/identity-service/.local/identity-tls.crt \
 -keystore .local/gateway-truststore.p12 -storetype PKCS12 -storepass changeit
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$(pwd)/.local/gateway-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit"
export FARMVERSE_JWT_ISSUER=https://localhost:8092
export FARMVERSE_JWKS_URI=https://localhost:8092/.well-known/jwks.json
export FARMVERSE_JWT_AUDIENCE=api-gateway
mvn -f platform/api-gateway/pom.xml spring-boot:run
```

Use a dedicated dev truststore/password, not this example password for production. Trust only your own certificate. The `IDENTITY_ISSUER` must exactly match the issuer expected by Gateway; `dev-tls` sets it to `https://localhost:8092`. The HTTPS listener replaces HTTP on port 8092. Spring Boot supports PEM certificate and private-key configuration for its embedded server.

## Get a token

```bash
curl -s --cacert .local/identity-tls.crt https://localhost:8092/.well-known/jwks.json
curl -s --cacert .local/identity-tls.crt -X POST https://localhost:8092/api/v1/auth/login \
 -H 'Content-Type: application/json' \
 -d '{"tenantId":"farm-demo","email":"admin@example.test","password":"replace-with-a-long-random-password"}'
```

The response contains authentication tokens when MFA is not required. A privileged account may instead need to complete the MFA challenge before receiving tokens. Never commit either token or the bootstrap password. Token audience, tenant, and scopes depend on the configured account and client.

## HTTP and gRPC API

The following HTTP routes describe the **current custom implementation**, not the adopted OAuth/OIDC target.

The same service also exposes Authorization Server endpoints: `GET /.well-known/openid-configuration`, `GET /oauth2/jwks`, `GET /userinfo`, `GET /oauth2/authorize`, and `POST /oauth2/token`. OAuth clients are configured with `farmverse.identity.oauth2.clients`; the default is empty, so there are no usable OAuth clients until a deployment provisions them. Authorization-code clients require exact redirect URIs and PKCE; configured clients are restricted to one resource audience. This OAuth surface is not yet used by Gateway/Web or all service callers.

- `POST /api/v1/auth/register` -> creates a USER only when public registration is enabled; disabled by default.
- `POST /api/v1/auth/login` -> authenticates using tenantId/email/password; privileged accounts may require MFA verification.
- `POST /api/v1/auth/bootstrap-superadmin` -> one-time initial super-admin creation; requires `X-FarmVerse-Bootstrap-Token` from the Identity startup log, unless `IDENTITY_SUPERADMIN_BOOTSTRAP_TOKEN` is explicitly configured.
- `POST /api/v1/auth/mfa/enrollment/begin` and `/confirm` -> challenge-token-protected TOTP enrollment.
- `POST /api/v1/auth/mfa/verify` -> completes an MFA login challenge.
- `POST /api/v1/auth/refresh` -> rotates a refresh token; the previous token is invalidated.
- `POST /api/v1/auth/logout` -> revokes the refresh-token family; an already issued JWT remains valid until expiry.
- `GET /api/v1/platform/deployment-state` -> reports whether first-super-admin bootstrap is required.
- `GET /.well-known/jwks.json` -> public RSA JWK only; no private key.

User, role, permission, session, and service-token administration are internal gRPC operations, not REST endpoints. The gRPC contract is in `libs/farmverse-proto/src/main/proto/farmverse/identity/v1/identity.proto`; see the platform gRPC documentation for available methods and authorities.

## Security design and remaining work

- BCrypt strength 12, password length of at least 12 characters and at most 72 UTF-8 bytes, login lockout after 5 failed attempts for 15 minutes; failed-attempt and refresh-reuse revocation updates commit even when HTTP 401 is returned, dummy password verification for unknown users.
- Legacy custom user access tokens use RS256, `kid`, issuer, audience, tenant, and subject. Their configured default TTL is 10 minutes and hard maximum is 15 minutes. Authorization Server access tokens have a separate 5-minute configured TTL and one resource audience. JWT revocation is **not instantaneous**: disabling an account or changing permissions does not invalidate already issued access tokens immediately.
- Refresh tokens are 48-byte random opaque values, stored only as SHA-256 hashes, rotated and grouped by family; reusing a revoked token revokes the family. A PostgreSQL integration test covers concurrent reuse, but it is release-gated and must be run against PostgreSQL.
- Privileged roles require TOTP; enrollment uses a purpose-bound challenge token. Recovery codes and replay protection are implemented; concurrent persistence behavior still requires PostgreSQL integration coverage.
- Role and permission catalog is currently global; admin assignments are tenant-scoped, but role/permission definitions are global and require platform authority. Tenant role assignment cannot delegate `PLATFORM_ADMIN`.
- Legacy service-token issue/exchange and directory/administration APIs remain over gRPC. OAuth `client_credentials` is available to explicitly configured clients; RFC 8693 exchange and caller migration remain open. Cross-host gRPC requires TLS/mTLS and private network controls.
- Rate limiting, CAPTCHA, email verification, password reset, distributed abuse detection, and a production secret-manager/rotation process remain open.
- Production requires PostgreSQL environment variables, runs Flyway migrations, and uses Hibernate schema validation. Migration and PostgreSQL concurrency gates must pass before production release; the profile defaults HTTP and gRPC binds to loopback.
- **Current status:** OAuth2/OIDC endpoints are implemented but not release-verified as interoperable. Do not treat the service as product-ready until PKCE/browser E2E, client migration, PostgreSQL V4 verification, secret rotation and operational release gates pass; see [identity-oauth2-oidc-target.md](../../docs/architecture/identity-oauth2-oidc-target.md).

**Operational warning:** Protect the bootstrap admin credentials; a PLATFORM_ADMIN can grant powerful global permissions. Do not expose this MVP to untrusted networks.

## PostgreSQL integration release gate

Run the Identity-owned persistence suite against a **disposable, non-production database**. It creates and retains the isolated `it_identity` schema:

```bash
export IDENTITY_IT_POSTGRES_URL='jdbc:postgresql://<host>:5432/<disposable-db>'
export IDENTITY_IT_POSTGRES_USER='<test-user>'
export IDENTITY_IT_POSTGRES_PASSWORD='<test-password>'
./services/identity-service/scripts/postgres-release-gate.sh
```

The test user needs permission to create objects in that database. Do not point this at production or a database containing non-disposable data. PostgreSQL is the Identity persistence boundary, so migration, transaction and concurrency tests belong in this module. This suite does not test service health aggregation or orchestrate database/broker processes.

## Operational runtime smoke check

Runtime startup orchestration is platform operations, not Identity functionality. The optional `scripts/health/identity-runtime-gate.sh` can check Identity's Actuator health against already-running PostgreSQL/MQTT services. It starts and stops only its own temporary Identity process; it never starts/stops the Termux dependencies. The gate is operational smoke coverage, not an Identity integration test or a production release requirement.
