# Configuration — SmartFarm Platform

> Verified @ HEAD `eaaa112`.

## Profile model
- `application.yml` (base): `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` — mặc định cứng `dev`.
- `application-dev.yml`: Postgres chung, H2-free, default secret dev.
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
| DB | `IDENTITY_DB_URL/USER/PASSWORD` (chỉ identity parameterized); 6 service khác hard-code shared URL ở dev |
| MQTT | `SMARTFARM_MQTT_URL`, `SMARTFARM_MQTT_EVENTS_ENABLED`, `SMARTFARM_MQTT_TOPIC_FILTER` |
| Gateway | `GATEWAY_SERVICE_CLIENT_ID/SECRET`, `GATEWAY_*_GRPC_DEADLINE` |

## Known config issues
- ISSUE-15: `ddl-auto` dev=`update` vs prod=`validate`.
- ISSUE-16: default secret trong `application-dev.yml` (dev-only).
- 6/7 service chưa env-parameterize DB URL cho per-service DB split.

← [Local Development](local-development.md)
