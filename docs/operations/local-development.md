# Local Development — SmartFarm Platform

> Verified @ HEAD `eaaa112`.

## Yêu cầu
JDK 17+, Maven 3.9+, Git, Docker Compose, Python 3 (cho `doctor.sh`/`bootstrap.sh`).

## Profile mặc định
Mọi module runnable (9) mặc định boot profile **`dev`** mà **không** cần set biến môi trường:
- Base `application.yml`: `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`.
- `spring-boot-maven-plugin` pin `<profile>dev</profile>`.
- Override: `SPRING_PROFILES_ACTIVE=prod java -jar ...` hoặc `mvn spring-boot:run -Dspring-boot.run.profiles=prod`.

> `farm-simulator` cố ý **không** pin `dev` (không có `application-dev.yml`, `web-application-type: none`, quarantined).

## Khởi động
```bash
./scripts/doctor.sh          # verify toolchain + 14 module
./scripts/bootstrap.sh       # mvn -B clean install
docker compose up -d         # postgres:17, mosquitto:2, redis:8
```

## Dev stack (profile dev)
| Service | HTTP | gRPC |
|---------|-----:|-----:|
| identity | 8092 | 9092 |
| livestock | 8081 | 9091 |
| inventory | 8083 | 9093 |
| finance | 8084 | 9094 |
| order | 8085 | 9095 |
| reporting | 8086 | 9096 |
| gateway | 8080 | — |

Thứ tự: identity trước (các service validate JWT qua JWKS của identity), rồi service, cuối cùng gateway.

```bash
mvn -f services/smartfarm-identity-service/pom.xml  spring-boot:run
mvn -f services/smartfarm-livestock-service/pom.xml spring-boot:run
mvn -f apps/smartfarm-gateway/pom.xml               spring-boot:run
```

## Database (dev)
Dev: 7 service **dùng chung** Postgres `smartfarm` (localhost:5432, postgres/root), Hibernate `ddl-auto: update` (Flyway off ở dev). TEST/PROD dự định per-service DB nhưng chỉ identity env-parameterized (`IDENTITY_DB_URL`).

## Smoke test
```bash
./scripts/release/v1/run-all.sh
curl http://localhost:8080/actuator/health
```

← [Configuration](configuration.md) · [Deployment](deployment.md)
