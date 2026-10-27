# Local Development — FarmVerse Platform

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
mvn -B clean verify
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
mvn -f services/identity-service/pom.xml  spring-boot:run
mvn -f services/livestock-service/pom.xml spring-boot:run
mvn -f platform/api-gateway/pom.xml               spring-boot:run
```

## Database (dev)
Identity dev defaults to `IDENTITY_DB_URL` or `jdbc:postgresql://localhost:5432/farmverse`, `postgres/root`; `ddl-auto: update`, Flyway disabled, and `open-in-view: false` are explicit in `services/identity-service/src/main/resources/application-dev.yml`. H2 is used by the test profile. Identity production instead requires `IDENTITY_DB_URL/USER/PASSWORD`, enables Flyway, validates the schema, and uses loopback bind addresses by default. Other module database settings are per their own `application-*.yml`; do not infer production database isolation from this identity setting.

Current service clients still use custom token issuance over Identity gRPC. HTTP also serves OAuth `/oauth2/authorize` and `/oauth2/token`, OIDC discovery/JWKS/UserInfo, while custom login/MFA/refresh routes remain during migration. OAuth clients are disabled unless `farmverse.identity.oauth2.clients` is configured; PKCE end-to-end, Gateway BFF migration and PostgreSQL V4 verification remain open (see [Identity flows](../services/identity-flows.md)). The ignored root `.env` may supply database and client secrets; do not enable shell tracing (`set -x`) while loading or using secrets.

```bash
set -a
. ./.env
set +a
mvn -f services/identity-service/pom.xml spring-boot:run
```

Run the command in the first terminal and leave it in the foreground. In a second user-owned terminal, start Gateway in the foreground:

```bash
set -a
. ./.env
set +a
mvn -f platform/api-gateway/pom.xml spring-boot:run
```

Stop both processes with Ctrl-C after testing; do not start them in the background. Identity's dev profile has local-only client credentials; production secrets must be independently provisioned for Gateway, Order, and Reporting. Identity's gRPC listener defaults to loopback. Do not widen it to cross-host traffic until TLS/mTLS and network access controls are configured.

The profile-binding regression test verifies that Identity loads all three dev service clients. Gateway delegated exchange and actor/tenant binding now have gRPC unit tests; actual server registration and multi-service runtime verification remain outstanding.

## Manual Gateway → Identity gRPC check

Identity's token RPC is internal and is not exposed through an HTTP test endpoint. To exercise the delegated gRPC flow through Gateway, start Identity and Gateway in the foreground as shown above, then use valid local credentials for an existing test user:

```bash
# Expected: Identity health UP; the dev database must be available.
curl -fsS http://localhost:8092/actuator/health
curl -fsS http://localhost:8080/actuator/health
```

Run this Python 3 smoke test in the foreground. It prompts for the password without echoing it, handles an MFA verification challenge, never prints access/refresh tokens, and calls GraphQL `me` through the Gateway:

```bash
python3 - <<'PY'
import getpass
import json
import urllib.error
import urllib.request

base = "http://localhost:8080"

def post(path, payload, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(
        base + path,
        data=json.dumps(payload).encode(),
        headers=headers,
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.load(response)

tenant_id = input("Tenant ID: ").strip()
email = input("Test account email: ").strip()
password = getpass.getpass("Password: ")
try:
    auth = post("/api/v1/auth/login", {
        "tenantId": tenant_id,
        "email": email,
        "password": password,
    })
    if auth.get("authenticationStatus") == "MFA_REQUIRED":
        method = input(f"MFA method {auth.get('availableMethods')}: ").strip()
        code = getpass.getpass("MFA code: ")
        auth = post("/api/v1/auth/mfa/verify", {
            "challengeToken": auth["challengeToken"],
            "method": method,
            "code": code,
        })
    if auth.get("authenticationStatus") != "COMPLETED":
        print("Authentication needs another step:", auth.get("authenticationStatus"))
        if auth.get("tenants"):
            print("Available tenants:", json.dumps(auth["tenants"]))
        raise SystemExit(2)
    access_token = auth.get("accessToken")
    if not access_token:
        raise SystemExit("Login completed without an access token")
    result = post("/graphql", {
        "query": "{ me { subjectId active profile { email } membership { tenantId status } } }"
    }, access_token)
    print(json.dumps(result, indent=2))
except urllib.error.HTTPError as error:
    print("Request failed with HTTP", error.code)
    raise SystemExit(1)
finally:
    password = None
```

A successful `me` query exercises Gateway JWT validation, `ExchangeDelegatedToken` over gRPC, and Identity directory gRPC. If login requires MFA enrollment or tenant selection, complete that flow first and rerun with the selected tenant. Never use `curl -v` or print access/refresh tokens. Focused automated checks: `mvn -f services/identity-service/pom.xml -Dtest=IdentityTokenGrpcServiceTest,IdentityTokenGrpcPolicyTest,ServiceTokenSettingsDevProfileTest,TokenServiceAuthTimeTest test`; Gateway/Order/Reporting client checks: run `-Dtest=ServiceTokenClientTest test` from each module POM.

## Smoke test
```bash
./scripts/run-all.sh        # runs the available health and smoke phases
curl http://localhost:8080/actuator/health
```

The restored unified shell runner currently contains health and smoke checks only. It does not include REST or GraphQL operation suites; check `scripts/README.md` for the exact available scripts.

← [Configuration](configuration.md) · [Deployment](deployment.md)
