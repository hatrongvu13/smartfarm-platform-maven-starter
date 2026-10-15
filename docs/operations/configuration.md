# Configuration — SmartFarm Platform

> Reconciled 2026-10-15 against current service profile files.

## Profile model
- `application.yml` (base): `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` — mặc định cứng `dev`.
- Identity `application-dev.yml`: local Postgres fallback, Hibernate `update`, Flyway off, Open Session in View on, dev HMAC defaults. Other modules have their own profile behavior; inspect their files rather than assuming the identity settings apply platform-wide.
- `application-prod.yml`: env-driven, nested `smartfarm.security.jwt.*`.
- `application-test.yml`: nested `jwt:` (CFG-01 đã fix).
- `spring-boot-maven-plugin` pin `dev` cho `mvn spring-boot:run`.

## Security config (bắt buộc nested — `SmartFarmSecurityProperties`)
```yaml
smartfarm:
  security:
    jwt:
      issuer: ${SMARTFARM_JWT_ISSUER}
      jwk-set-uri: ${SMARTFARM_JWKS_URI}
      audiences:
        - ${SMARTFARM_JWT_AUDIENCE:smartfarm-<service>}
```
> ⚠️ Dạng **flat** (`smartfarm.security.issuer`) khiến `jwt` bind null → service không boot. Đây là nguyên nhân ISSUE-01/CFG-01.

## Biến môi trường chính
| Nhóm | Biến |
|------|------|
| JWT | `SMARTFARM_JWT_ISSUER`, `SMARTFARM_JWKS_URI`, `SMARTFARM_JWT_AUDIENCE`, `IDENTITY_ISSUER`, `IDENTITY_JWK_SET_URI` |
| gRPC endpoints | `*_GRPC_HOST`, `*_GRPC_PORT` (identity 9092, livestock 9091, inventory 9093, finance 9094, order 9095, reporting 9096) |
| DB | Identity uses `IDENTITY_DB_URL/USER/PASSWORD` (local fallback only in dev; required in prod); each other module has its own datasource configuration |
| MQTT | `SMARTFARM_MQTT_URL`, `SMARTFARM_MQTT_EVENTS_ENABLED`, `SMARTFARM_MQTT_TOPIC_FILTER` |
| Gateway / Identity internal token minting | `IDENTITY_INTERNAL_API_SECRET` must have the same independently managed value in gateway and identity; required in prod and sent as `X-Identity-Internal-Secret` |
| Gateway | `GATEWAY_SERVICE_CLIENT_ID/SECRET`, `GATEWAY_*_GRPC_DEADLINE` |

## Known config issues
- Identity dev uses `ddl-auto=update`, Flyway off, and `open-in-view=true`; prod uses Flyway and `ddl-auto=validate`.
- ISSUE-16: default secret trong `application-dev.yml` (dev-only).
- 6/7 service chưa env-parameterize DB URL cho per-service DB split.

← [Local Development](local-development.md)
