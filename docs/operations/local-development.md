# Local Development — SmartFarm Platform

> Reconciled 2026-10-15 against root POM, service profiles, and script contents.

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
./scripts/doctor.sh          # verify toolchain + reactor module POMs
./scripts/build-all.sh       # mvn -B clean verify
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
Identity dev defaults to `IDENTITY_DB_URL` or `jdbc:postgresql://localhost:5432/smartfarm`, `postgres/root`; `ddl-auto: update`, Flyway disabled, and `open-in-view: true` are explicit in `services/smartfarm-identity-service/src/main/resources/application-dev.yml`. Identity production instead requires `IDENTITY_DB_URL/USER/PASSWORD`, enables Flyway, validates the schema, and uses loopback bind addresses by default. Other module database settings are per their own `application-*.yml`; do not infer production database isolation from this identity setting.

For local Gateway-to-Identity service-token requests, export the same private transport secret to both processes before starting them:

```bash
export IDENTITY_INTERNAL_API_SECRET="$(openssl rand -hex 32)"
mvn -f services/smartfarm-identity-service/pom.xml spring-boot:run
mvn -f apps/smartfarm-gateway/pom.xml spring-boot:run
```

In separate terminals, load the same value from a local ignored env file rather than generating it twice. Identity's dev profile supplies a clearly development-only fallback; an explicit environment value overrides it. The Gateway will fail closed if it has no secret, and production requires `IDENTITY_INTERNAL_API_SECRET` to be configured independently in both services. This shared header secret does not encrypt traffic; use private networking and TLS/mTLS across hosts.

**Current runtime caveat (2026-10-15):** the checked-in dev YAML declares the Gateway service-token client, but a local Identity startup logged `clientCount=0`; issuance with the correct shared header still returned 401 (`bad_credentials`). The header check itself rejected missing and incorrect values. Until the client configuration binding issue is fixed and verified, do not assume these commands establish a working Gateway-to-Identity token flow.

## Smoke test
```bash
./scripts/run-all.sh        # runs the available health and smoke phases
curl http://localhost:8080/actuator/health
```

The restored unified shell runner currently contains health and smoke checks only. It does not include REST or GraphQL operation suites; check `scripts/README.md` for the exact available scripts.

← [Configuration](configuration.md) · [Deployment](deployment.md)
