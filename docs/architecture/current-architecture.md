# Kiến trúc hiện tại

Mô tả đúng những gì có trong code ngày 2026-11-04. Mỗi dòng có bằng chứng.

## 1. Module

Khai trong [`pom.xml`](../../pom.xml): Spring Boot 4.1.2 (parent), Spring Cloud 2025.1.4 (BOM), Java 17.

| Module | Cổng | Công nghệ chính | Bằng chứng |
|---|---|---|---|
| `platform/api-gateway` | 8080 | Spring Cloud Gateway (WebFlux), OAuth2 Client, Spring Session Redis | [`pom.xml`](../../platform/api-gateway/pom.xml), [`SecurityConfiguration`](../../platform/api-gateway/src/main/java/com/htv/farmverse/gateway/config/SecurityConfiguration.java) |
| `services/identity-service` | 8092, quản trị 9092 (profile `mtls`) | Spring Authorization Server, JPA, Flyway, TOTP | [`SecurityConfiguration`](../../services/identity-service/src/main/java/com/htv/farmverse/identity/config/SecurityConfiguration.java) |
| `services/farm-service` | 8082, quản trị 9082 (profile `mtls`) | OAuth2 Resource Server, JPA, Hibernate `@TenantId` | [`SecurityConfiguration`](../../services/farm-service/src/main/java/com/htv/farmverse/farm/config/SecurityConfiguration.java) |
| `libs/farmverse-starter-service` | — | Converter JWT, validator, `ApiProblem`, mTLS client | [`FarmVerseJwtValidators`](../../libs/farmverse-starter-service/src/main/java/com/htv/farmverse/service/security/FarmVerseJwtValidators.java), [`MtlsClients`](../../libs/farmverse-starter-service/src/main/java/com/htv/farmverse/service/mtls/MtlsClients.java) |
| `apps/farmverse-web` | 5173 (dev) | React + Vite, chỉ dùng cookie | [`package.json`](../../apps/farmverse-web/package.json) |

Hạ tầng dev: [`compose.yaml`](../../compose.yaml) có PostgreSQL 17 và Redis 8 (mật khẩu `REDIS_PASSWORD`, mặc định `farmverse-dev`), cả hai chỉ mở ở `127.0.0.1`. Service chạy ngoài compose.

## 2. Sơ đồ thành phần

```mermaid
flowchart TB
    subgraph Browser[Trình duyệt]
        SPA[farmverse-web<br/>chỉ có cookie]
    end
    subgraph GW[api-gateway]
        SEC[SecurityWebFilterChain<br/>oauth2Login + PKCE, CSRF cookie]
        ROUTES[Routes: identity-api, farm-api, web<br/>TokenRelay + RequestRateLimiter]
        BFF[BffController /bff/me]
        ACT[Actuator: health, info, gateway]
    end
    subgraph ID[identity-service]
        SAS[SAS: /oauth2/*, /connect/logout, /userinfo]
        LOGIN[Form login tenant-aware + TOTP]
        ADM[REST quản trị /users /roles /tenants]
        SIF1[ServiceIdentityFilter<br/>gateway, farm]
    end
    subgraph FARM[farm-service]
        SIF2[ServiceIdentityFilter<br/>gateway]
        RS[Resource server<br/>JwtDecoder + validator]
        API[FarmController + @PreAuthorize]
        TEN[TenantFromTokenResolver → @TenantId]
    end
    SPA --> SEC --> ROUTES
    ROUTES --> SIF2 --> RS --> API --> TEN
    ROUTES --> SIF1 --> ADM
    SEC --> SAS
    RS -. JWKS .-> SAS
    GW --- REDIS[(Redis)]
    ID --- PG1[(farmverse_identity)]
    FARM --- PG2[(farmverse_farm)]
```

## 3. Gateway (BFF)

| Việc | Hiện trạng | Bằng chứng |
|---|---|---|
| Đăng nhập | `oauth2Login` + PKCE `S256`; `state`, `nonce` mới cho mỗi lần; đổi code gửi `code_verifier`; `state` sai thì không đăng nhập | `SecurityConfiguration#security`, [`LoginSessionSecurityTest`](../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/LoginSessionSecurityTest.java) |
| Client OIDC | Khai tĩnh, không discovery, để gateway khởi động khi identity chưa chạy | [`OidcClientConfiguration`](../../platform/api-gateway/src/main/java/com/htv/farmverse/gateway/config/OidcClientConfiguration.java) |
| Lưu token | `WebSessionServerOAuth2AuthorizedClientRepository` → WebSession → Spring Session Redis | `SecurityConfiguration#authorizedClientRepository` |
| Cookie phiên | `FV_SESSION` opaque, `HttpOnly`, `SameSite=Lax`, `Path=/`, không `Domain`; `Secure` khi `farmverse.gateway.secure-cookie=true` (bật ở [`application-prod.yml`](../../platform/api-gateway/src/main/resources/application-prod.yml), có test khóa) | `SecurityConfiguration#webSessionIdResolver`, [`SessionCookieAttributesTest`](../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/config/SessionCookieAttributesTest.java), [`LoginSessionSecurityTest`](../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/LoginSessionSecurityTest.java) |
| Chống session fixation | Mặc định Spring Security WebFlux đổi id phiên khi đăng nhập; id cũ trả 401. Có test, và đã đối chứng: tắt đổi id thì test đỏ | [`LoginSessionSecurityTest`](../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/LoginSessionSecurityTest.java) |
| CSRF | `CookieServerCsrfTokenRepository.withHttpOnlyFalse()` (cookie `XSRF-TOKEN` + header/field) | `SecurityConfiguration#security`, test `logoutFormWithCsrfFieldIsAccepted_andWithoutItIsRejected` |
| Gọi xuống service | `TokenRelay` (tự refresh), `StripPrefix=2` | [`application.yml`](../../platform/api-gateway/src/main/resources/application.yml) |
| Rate limit | `RequestRateLimiter` Redis, key = tên principal, 20/s, burst 40. Redis lỗi thì bộ giới hạn **cho qua** (hành vi của Spring Cloud Gateway), nhưng phiên đã bị chặn trước đó | `application.yml`, `SecurityConfiguration#principalKeyResolver` |
| Redis | Mật khẩu bắt buộc, timeout lệnh `${REDIS_TIMEOUT:2s}`, kết nối `${REDIS_CONNECT_TIMEOUT:1s}`; phiên `${GATEWAY_SESSION_TIMEOUT:30m}`, namespace `farmverse:gateway:session`; Redis lỗi → 503 `SF-GATEWAY-503` (M3) | `application.yml`, [`RedisUnavailableHandler`](../../platform/api-gateway/src/main/java/com/htv/farmverse/gateway/config/RedisUnavailableHandler.java), [`RedisSessionHardeningTest`](../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/RedisSessionHardeningTest.java) |
| Timeout xuống service | Chỉ ở profile `mtls`: connect 3s, response 10s | [`application-mtls.yml`](../../platform/api-gateway/src/main/resources/application-mtls.yml) |
| Đăng xuất | RP-initiated logout: chuyển trình duyệt tới `/connect/logout` của identity | `OidcClientInitiatedServerLogoutSuccessHandler`, test `logoutRedirectsTheBrowserToIdentityEndSession` |
| `/bff/me` | Đọc claim từ access token để hiển thị, không trả token | [`BffController`](../../platform/api-gateway/src/main/java/com/htv/farmverse/gateway/bff/BffController.java) |
| Actuator | Cổng quản trị riêng `${GATEWAY_MANAGEMENT_PORT:9080}`, bind `127.0.0.1`, chỉ `health,info` (M2, 2026-11-04). Cổng 8080 không còn `/actuator/**`. Endpoint `gateway` không bật | `application.yml` khối `management`, [`GatewayActuatorExposureTest`](../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/GatewayActuatorExposureTest.java), [ADR-0008](adr/0008-gateway-management-port.md) |
| Header bảo mật (CSP, HSTS) | Không cấu hình riêng; chỉ có mặc định của Spring Security | — |

## 4. Identity

| Việc | Hiện trạng | Bằng chứng |
|---|---|---|
| OIDC provider | SAS; client đăng ký từ cấu hình, PKCE bắt buộc, refresh token xoay vòng (`reuseRefreshTokens(false)`), access token 5 phút, refresh 14 ngày | [`OAuthClientProvisioner`](../../services/identity-service/src/main/java/com/htv/farmverse/identity/oauth2/OAuthClientProvisioner.java), [`IdentityProperties`](../../services/identity-service/src/main/java/com/htv/farmverse/identity/config/IdentityProperties.java) |
| Claim token | `aud=farmverse-api` (chung mọi API), `tenant_id`, `membership_id`, `roles`, `perms`, `token_type` | [`FarmVerseTokenCustomizer`](../../services/identity-service/src/main/java/com/htv/farmverse/identity/oauth2/FarmVerseTokenCustomizer.java) |
| Khóa ký | JWKS nhiều khóa, khóa nghỉ vẫn công bố | [`SigningKeyConfiguration`](../../services/identity-service/src/main/java/com/htv/farmverse/identity/oauth2/SigningKeyConfiguration.java), `SigningKeyRotationTest`, [`key-rotation.md`](../operations/key-rotation.md) |
| TOTP | Secret mã hóa `Encryptors.delux` (AES-GCM); chống replay bằng `lastUsedStep`; sai mã tính vào khóa tạm tài khoản; mã khôi phục dùng một lần | [`TotpService`](../../services/identity-service/src/main/java/com/htv/farmverse/identity/mfa/TotpService.java), [`UserTotp`](../../services/identity-service/src/main/java/com/htv/farmverse/identity/mfa/UserTotp.java), `TotpLoginFlowTest`, `RecoveryCodeServiceTest` |
| Chống dò | Khóa tài khoản theo số lần sai; giới hạn theo IP cho `POST /login` và `POST /login/totp` (10/phút, **trong bộ nhớ một instance**) | [`LoginRateLimitFilter`](../../services/identity-service/src/main/java/com/htv/farmverse/identity/login/LoginRateLimitFilter.java), `LoginRateLimitFilterTest` |
| Audit | Logger `SECURITY_AUDIT`, làm sạch giá trị, cấm ghi mật khẩu/mã/token | [`SecurityAudit`](../../services/identity-service/src/main/java/com/htv/farmverse/identity/audit/SecurityAudit.java), `SecurityAuditTest`, `AdminAuditTest` |
| Log có cấu trúc | ECS JSON chỉ ở `application-prod.yml` của identity | [`application-prod.yml`](../../services/identity-service/src/main/resources/application-prod.yml) |

## 5. Farm

| Việc | Hiện trạng | Bằng chứng |
|---|---|---|
| Kiểm JWT | Chữ ký + `exp`/`nbf` + `iss` (`JwtValidators.createDefaultWithIssuer`) + `aud` chứa `farmverse-api` | `FarmVerseJwtValidators`, [`MtlsConfiguration`](../../services/farm-service/src/main/java/com/htv/farmverse/farm/config/MtlsConfiguration.java); không `mtls`: thuộc tính `spring.security.oauth2.resourceserver.jwt.*` trong [`application.yml`](../../services/farm-service/src/main/resources/application.yml) |
| JWKS | Tải từ identity, cache của `NimbusJwtDecoder` (mặc định, chưa cấu hình riêng). Không gọi identity mỗi request | `MtlsClients#jwtDecoder` |
| Phân quyền | `@PreAuthorize` theo `perms` (`farm:farm:read/write`) hoặc role `SUPER_ADMIN`. **Không** kiểm `scope` | [`FarmController`](../../services/farm-service/src/main/java/com/htv/farmverse/farm/adapter/in/rest/FarmController.java) |
| Tenant | Hibernate `@TenantId`, tenant lấy từ claim `tenant_id`; không có token → tenant giả `__none__` | [`TenantFromTokenResolver`](../../services/farm-service/src/main/java/com/htv/farmverse/farm/adapter/out/jpa/TenantFromTokenResolver.java), `TenantIsolationTest` |
| Ownership trong tenant | **Chưa có** (farm không có chủ sở hữu; mọi người cùng tenant có quyền như nhau theo `perms`) | — |
| Đồng thời | `@Version`; 409 `SF-FARM-010` / `SF-FARM-011` (chưa commit) | [`FarmExceptionHandler`](../../services/farm-service/src/main/java/com/htv/farmverse/farm/adapter/in/rest/FarmExceptionHandler.java), `FarmControllerTest` |

## 6. mTLS

Profile `mtls`; `prod` kéo theo `mtls` (`spring.profiles.group.prod: [mtls]` trong mỗi `application.yml`).

| Chặng | Hiện trạng | Bằng chứng |
|---|---|---|
| gateway → farm | `client-auth: need`; chỉ SAN `spiffe://farmverse/gateway` | [`application-mtls.yml` farm](../../services/farm-service/src/main/resources/application-mtls.yml), [`ServiceIdentityFilter`](../../libs/farmverse-starter-service/src/main/java/com/htv/farmverse/service/mtls/ServiceIdentityFilter.java), farm `MtlsIntegrationTest` |
| gateway/farm → identity | `client-auth: want`; đường máy-gọi-máy đòi SAN `gateway` hoặc `farm` | [`application-mtls.yml` identity](../../services/identity-service/src/main/resources/application-mtls.yml), identity `MtlsIntegrationTest` |
| Gateway trình cert | Route dùng SSL bundle; WebClient riêng cho token/JWKS | gateway `application-mtls.yml`, `MtlsClientTest` |
| Cert dev | 1 CA + 1 cert mỗi service | [`scripts/generate-dev-mtls.sh`](../../scripts/generate-dev-mtls.sh) |

## 7. Dữ liệu

| Việc | Hiện trạng | Bằng chứng |
|---|---|---|
| Database | Hai database riêng `farmverse_identity`, `farmverse_farm` trên một PostgreSQL | [`infra/postgres-init.sql`](../../infra/postgres-init.sql) |
| User DB | Dev: cả hai service dùng user `postgres` (superuser). Prod: biến `IDENTITY_DB_USER`, `FARM_DB_USER` riêng, nhưng **chưa có** script tạo user/quyền | `application-dev.yml`, `application-prod.yml` của từng service |
| Migration | Flyway, `ddl-auto: validate` | `services/*/src/main/resources/db/migration/` |

## 8. Build, test, đóng gói

| Việc | Hiện trạng |
|---|---|
| Build | Maven, chưa có wrapper (`mvnw`). Lệnh baseline: `mvn -o -B test` |
| Test | 22 lớp test; dùng H2 và cert do script tạo. Không cần Docker |
| Test phụ thuộc môi trường | Đã sửa (M2a): test gateway dùng kho session trong bộ nhớ ([`InMemorySessionTestConfiguration`](../../platform/api-gateway/src/test/java/com/htv/farmverse/gateway/InMemorySessionTestConfiguration.java)), không cần Redis. Hành vi riêng của Redis vẫn chưa có test |
| Docker | [`Dockerfile`](../../Dockerfile) chung, user không root. Trong container mọi service dùng `SERVER_PORT=8080` và `MANAGEMENT_SERVER_PORT=9090` (loopback); healthcheck gọi `http://127.0.0.1:9090/actuator/health/readiness`; chỉ `EXPOSE 8080` (M2) |
| CI | [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml): `mvn -B verify` rồi `docker build` cho 3 service (không push). Action ghim SHA. **Chưa chạy trên GitHub** (chưa push) |
| CD | Không có |
| Observability | Actuator health/info. **Không có** Micrometer Tracing, exporter metric |

## 9. Kết quả baseline (2026-11-04)

```
/Users/jaxmac/sdk/apache-maven-3.9.16/bin/mvn -o -B test
farmverse-starter-service  SUCCESS
identity-service           SUCCESS
farm-service               SUCCESS (13 test)
api-gateway                FAILURE: 2/5 test, Redis không chạy trên máy
```

Lần chạy trước trong cùng ngày (khi Redis còn chạy) toàn bộ xanh. Đây là bằng chứng test gateway không độc lập môi trường (P2-6).

Sau M2a:

```
REDIS_PORT=1 mvn -o -B test        # Redis trỏ cổng chết
service-support 4 · identity 39 · farm 13 · gateway 6   BUILD SUCCESS
```

Đối chứng: bỏ `@Import(InMemorySessionTestConfiguration.class)` thì 2 test logout đỏ lại.
