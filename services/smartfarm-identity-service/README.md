# SmartFarm Identity Service | Java 17, Maven, Spring Boot 4

Self-hosted identity **MVP** with no third-party identity application: local user store, login, registration (disabled by default), admin-created users, roles/permissions, RS256 JWT access tokens, public JWKS and hashed rotating refresh tokens. Uses Spring Security libraries, not Keycloak. **This is not an OAuth2/OIDC-compliant authorization server.** Do not claim OIDC discovery, PKCE, the OAuth2 client-credentials grant, or browser SSO support.

## Setup and run (dev)

Place this folder at `services/smartfarm-identity-service/`. Add `<module>services/smartfarm-identity-service</module>` to root `pom.xml`.

```bash
cd services/smartfarm-identity-service
./scripts/generate-dev-keys.sh
mvn clean spring-boot:run
```

The `dev` profile uses PostgreSQL (`IDENTITY_DB_URL`, defaulting to `jdbc:postgresql://localhost:5432/smartfarm`) with Hibernate schema update; H2 is used by the `test` profile. Generated RSA keypair is local and ignored by Git. **Do not regenerate keys** after creating accounts unless you intentionally invalidate existing JWTs. Create the first super-admin once through `POST /api/v1/auth/bootstrap-superadmin`; submit its credentials in that request, not through environment properties. Privileged users complete MFA enrollment through the challenge-token-protected endpoints. `IDENTITY_BOOTSTRAP_TENANT` configures the platform authorization tenant, not the bootstrap account. Use `dev-tls` for HTTP end-to-end tests. Never expose plaintext login/token endpoints outside an isolated localhost development session.

In the `dev` profile, Identity automatically creates a random 256-bit MFA encryption key at `.local/identity-mfa-encryption-key` on first launch and reuses it on subsequent launches; the file is owner-readable/writable and ignored by Git. Set `IDENTITY_MFA_ENCRYPTION_KEY` to override it, or `IDENTITY_MFA_ENCRYPTION_KEY_FILE` to change the local file path. For non-dev deployments, provide `IDENTITY_MFA_ENCRYPTION_KEY` as a Base64-encoded 32-byte secret; startup fails if it is missing or malformed. Keep the key private and stable while encrypted MFA enrollments are in use: replacing it makes existing MFA secrets unreadable. If the previously committed development key was used for MFA enrollments, reset those enrollments before switching to a new key.

## Connect the existing Gateway

The previously supplied `smartfarm-security` library requires **HTTPS** issuer/JWKS. To run Identity and Gateway together locally **without a third-party identity application or reverse proxy**, use Spring Boot's embedded HTTPS server:

```bash
cd services/smartfarm-identity-service
./scripts/generate-dev-keys.sh       # only once; do not overwrite existing signing keys
./scripts/generate-dev-tls.sh        # only once for the local certificate
export IDENTITY_BOOTSTRAP_TENANT=farm-demo
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

The response contains authentication tokens when MFA is not required. A privileged account may instead need to complete the MFA challenge before receiving tokens. Never commit either token or the bootstrap password. Token audience, tenant, and scopes depend on the configured account and client.

## HTTP and gRPC API

- `POST /api/v1/auth/register` -> creates a USER only when public registration is enabled; disabled by default.
- `POST /api/v1/auth/login` -> authenticates using tenantId/email/password; privileged accounts may require MFA verification.
- `POST /api/v1/auth/bootstrap-superadmin` -> one-time initial super-admin creation.
- `POST /api/v1/auth/mfa/enrollment/begin` and `/confirm` -> challenge-token-protected TOTP enrollment.
- `POST /api/v1/auth/mfa/verify` -> completes an MFA login challenge.
- `POST /api/v1/auth/refresh` -> rotates a refresh token; the previous token is invalidated.
- `POST /api/v1/auth/logout` -> revokes the refresh-token family; an already issued JWT remains valid until expiry.
- `GET /api/v1/platform/deployment-state` -> reports whether first-super-admin bootstrap is required.
- `GET /.well-known/jwks.json` -> public RSA JWK only; no private key.

User, role, permission, session, and service-token administration are internal gRPC operations, not REST endpoints. The gRPC contract is in `libs/smartfarm-proto/src/main/proto/smartfarm/identity/v1/identity.proto`; see the platform gRPC documentation for available methods and authorities.

## Security design and remaining work

- BCrypt strength 12, password length of at least 12 characters and at most 72 UTF-8 bytes, login lockout after 5 failed attempts for 15 minutes; failed-attempt and refresh-reuse revocation updates commit even when HTTP 401 is returned, dummy password verification for unknown users.
- Access tokens use RS256, `kid`, issuer, audience, tenant, and subject. The configured default TTL is 10 minutes and the hard maximum is 15 minutes. JWT revocation is **not instantaneous**: disabling an account or changing permissions does not invalidate already issued access tokens immediately.
- Refresh tokens are 48-byte random opaque values, stored only as SHA-256 hashes, rotated and grouped by family; reusing a revoked token revokes the family. **Concurrency and replay handling require integration tests under PostgreSQL**.
- Privileged roles require TOTP; enrollment uses a purpose-bound challenge token. Recovery codes and replay protection are implemented, but concurrent persistence behavior still requires PostgreSQL integration coverage.
- Role and permission catalog is currently global; admin assignments are tenant-scoped, but role/permission definitions are global and require platform authority. Tenant role assignment cannot delegate `PLATFORM_ADMIN`.
- Service-token issue/exchange and directory/administration APIs are implemented over gRPC. Machine issue validates registered client credentials and audience allowlists; delegated exchange additionally requires a verified user JWT. Cross-host gRPC requires TLS/mTLS and private network controls.
- Rate limiting, CAPTCHA, email verification, password reset, distributed abuse detection, and a production secret-manager/rotation process remain open.
- Production requires PostgreSQL environment variables, runs Flyway migrations, and uses Hibernate schema validation. Migration and PostgreSQL concurrency gates must pass before production release; the profile defaults HTTP and gRPC binds to loopback.
- This service is deliberately a limited self-hosted authentication service, **not** a full standards-compliant OAuth2/OIDC provider. If browser/mobile OAuth interoperability is needed later, implement standards using maintained Spring Authorization Server components within this service, rather than inventing a partial protocol.

**Operational warning:** Protect the bootstrap admin credentials; a PLATFORM_ADMIN can grant powerful global permissions. Do not expose this MVP to untrusted networks.
