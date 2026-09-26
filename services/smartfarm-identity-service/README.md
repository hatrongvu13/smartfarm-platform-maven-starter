# SmartFarm Identity Service | Java 17, Maven, Spring Boot 4

Self-hosted identity **MVP** with no third-party identity application: local user store, login, registration (disabled by default), admin-created users, roles/permissions, RS256 JWT access tokens, public JWKS and hashed rotating refresh tokens. Uses Spring Security libraries, not Keycloak. **This is not an OAuth2/OIDC-compliant authorization server.** Do not claim OIDC discovery, PKCE, client credentials or browser SSO support.

## Setup and run (dev)

Place this folder at `services/smartfarm-identity-service/`. Add `<module>services/smartfarm-identity-service</module>` to root `pom.xml`.

```bash
cd services/smartfarm-identity-service
./scripts/generate-dev-keys.sh
export IDENTITY_BOOTSTRAP_TENANT=farm-demo
export IDENTITY_BOOTSTRAP_EMAIL=admin@example.test
export IDENTITY_BOOTSTRAP_PASSWORD='replace-with-a-long-random-password'
mvn clean spring-boot:run
```

Dev DB is file-backed H2 under `.local/`; generated RSA keypair is local, ignored by Git. **Do not regenerate keys** after creating accounts unless you intentionally invalidate existing JWTs. Bootstrap creates the first admin only if the account does not already exist. Remove bootstrap environment variables after initial setup. Use `dev-tls` for any end-to-end tests. Never expose plaintext login/token endpoints outside an isolated localhost development session.

## Connect the existing Gateway

The previously supplied `smartfarm-security` library requires **HTTPS** issuer/JWKS. To run Identity and Gateway together locally **without a third-party identity application or reverse proxy**, use Spring Boot's embedded HTTPS server:

```bash
cd services/smartfarm-identity-service
./scripts/generate-dev-keys.sh       # only once; do not overwrite existing signing keys
./scripts/generate-dev-tls.sh        # only once for the local certificate
export IDENTITY_BOOTSTRAP_TENANT=farm-demo
export IDENTITY_BOOTSTRAP_EMAIL=admin@example.test
export IDENTITY_BOOTSTRAP_PASSWORD='replace-with-a-long-random-password'
mvn spring-boot:run -Dspring-boot.run.profiles=dev,dev-tls
```

In the Gateway terminal, trust the **dev certificate** using a local truststore (never use `trustAll`):

```bash
mkdir -p .local
printf '\n.local/\n' >> .gitignore # add once to the ROOT workspace gitignore
keytool -importcert -noprompt -alias smartfarm-identity-dev \
 -file services/smartfarm-identity-service/.local/identity-tls.crt \
 -keystore .local/gateway-truststore.p12 -storetype PKCS12 -storepass changeit
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$(pwd)/.local/gateway-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit"
export SMARTFARM_JWT_ISSUER=https://localhost:8092
export SMARTFARM_JWKS_URI=https://localhost:8092/.well-known/jwks.json
export SMARTFARM_JWT_AUDIENCE=smartfarm-gateway
mvn -f apps/smartfarm-gateway/pom.xml spring-boot:run
```

Use a dedicated dev truststore/password, not this example password for production. Trust only your own certificate. The `IDENTITY_ISSUER` must exactly match the issuer expected by Gateway; `dev-tls` sets it to `https://localhost:8092`. The HTTPS listener replaces HTTP on port 8092. Spring Boot supports PEM certificate and private-key configuration for its embedded server.

## Get a token

```bash
curl -s --cacert .local/identity-tls.crt https://localhost:8092/.well-known/jwks.json
curl -s --cacert .local/identity-tls.crt -X POST https://localhost:8092/api/v1/auth/login \
 -H 'Content-Type: application/json' \
 -d '{"tenantId":"farm-demo","email":"admin@example.test","password":"replace-with-a-long-random-password"}'
```

The response contains `accessToken` and `refreshToken`. Never commit either token or the bootstrap password. Use the access token as `Authorization: Bearer ...` at Gateway; `aud=smartfarm-gateway`, `tenant_id`, and `scope=farm:read identity:admin identity:platform` are included for the seeded ADMIN role.

## API

- `POST /api/v1/auth/login` tenantId/email/password -> access/refresh.
- `POST /api/v1/auth/refresh` `{ "refreshToken": "..." }` -> new access/refresh; previous refresh token is invalidated.
- `POST /api/v1/auth/logout` -> revoke refresh family; current JWT remains valid until expiry.
- `GET /api/v1/auth/me` -> authenticated identity and current permissions.
- `GET /api/v1/auth/verify` -> verifies JWT and checks that the account is currently enabled (not a public token introspection endpoint).
- `POST /api/v1/auth/password` -> change own password and revoke refresh sessions.
- `POST /api/v1/auth/register` -> USER account only if `public-registration=true`, with a **fixed** registration tenant; disabled by default.
- `POST /api/v1/admin/users` -> create USER in admin's tenant.
- `PUT /api/v1/admin/users/{id}/roles/{role}` -> assign existing role in same tenant.
- `DELETE /api/v1/admin/users/{id}/roles/{role}` -> remove role; cannot remove own ADMIN.
- `POST /api/v1/admin/users/{id}/disable` -> disable account; cannot disable self.
- `POST /api/v1/admin/roles/{role}` -> define a global role (PLATFORM_ADMIN only).
- `PUT /api/v1/admin/roles/{role}/permissions/{permission}` -> grant scope to role (PLATFORM_ADMIN only).
- `GET /.well-known/jwks.json` -> public RSA JWK only; no private key.

Example admin creates a user:

```bash
curl --cacert .local/identity-tls.crt -X POST https://localhost:8092/api/v1/admin/users \
 -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' \
 -d '{"email":"worker@example.test","password":"a-long-unique-password-123"}'
```

Example add permission admin:
```bash
curl -X PUT \
  http://localhost:8092/api/v1/admin/roles/ADMIN/permissions/tasks:write \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Security design and remaining work

- BCrypt strength 12, password length 12..72, login lockout after 5 failed attempts for 15 minutes; failed-attempt and refresh-reuse revocation updates commit even when HTTP 401 is returned, dummy password verification for unknown users.
- Access tokens RS256, `kid`, issuer, audience, tenant, subject, 10-minute TTL. JWT revocation is **not instantaneous**: disabling an account or changing permissions stops new tokens but old access tokens remain valid until expiry; add distributed token-version/revocation if required.
- Refresh tokens are 48-byte random opaque values, stored only as SHA-256 hashes, rotated and grouped by family; reusing a revoked token revokes the family. **Concurrency and replay handling require integration tests under PostgreSQL**.
- Role and permission catalog is currently global; admin assignments are tenant-scoped, but admin role/permission *definitions* are global and require `SCOPE_identity:platform`. Bootstrap admin has this permission; ordinary tenant admins do not. Tenant role assignment cannot delegate `PLATFORM_ADMIN`. Review platform-admin provisioning before production.
- No rate limiter, CAPTCHA, MFA, email verification, password reset, security audit, distributed abuse detection, key rotation, secret manager, token exchange or gRPC directory adapter yet. Add these before public exposure.
- `schema.sql` initializes dev H2. For production use PostgreSQL with versioned Flyway migrations; `application-prod.yml` disables automatic SQL initialization. Do not run prod until schema migration and HTTPS reverse proxy are deployed.
- This service is deliberately a limited self-hosted authentication service, **not** a full standards-compliant OAuth2/OIDC provider. If browser/mobile OAuth interoperability is needed later, implement standards using maintained Spring Authorization Server components within this service, rather than inventing a partial protocol.

**Operational warning:** Protect the bootstrap admin credentials; a PLATFORM_ADMIN can grant powerful global permissions. Do not expose this MVP to untrusted networks.
