package com.htv.smartfarm.finance.it;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Reusable base for finance PostgreSQL integration tests, running against a REAL PostgreSQL 17
 * (matching compose.yaml / prod) — never H2. Runtime schema strategy mirrors prod:
 *
 *   PostgreSQL -> Flyway migrate -> Hibernate ddl-auto=validate -> context -> test
 *
 * Two execution modes, auto-selected:
 *   1. EXTERNAL (no Docker): pass -Dit.postgres.url=jdbc:postgresql://host:5432/db
 *      [-Dit.postgres.user=.. -Dit.postgres.pass=..]. Uses a dedicated schema (it_finance)
 *      so it never collides with real data. This is how the IT runs on a host without Docker.
 *   2. TESTCONTAINERS (Docker present, no it.postgres.url): spins up ONE shared postgres:17
 *      container for the JVM. This is the CI path.
 *
 * The whole IT is @EnabledIfSystemProperty on it.postgres.enabled=true, so it never runs in a
 * plain `mvn test` on a machine with neither Docker nor a provided DB (which would just error).
 * Enable with -Dit.postgres.enabled=true (+ -Dit.postgres.url for the external mode).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=true",
                "spring.flyway.baseline-on-migrate=false",
                "spring.flyway.schemas=it_finance",
                "spring.flyway.default-schema=it_finance",
                "spring.flyway.create-schemas=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.default_schema=it_finance",
                "spring.grpc.server.port=0",
                "smartfarm.security.jwt.issuer=http://localhost:8092",
                "smartfarm.security.jwt.jwk-set-uri=http://localhost:8092/.well-known/jwks.json",
                "smartfarm.security.jwt.audiences=smartfarm-finance",
                "smartfarm.security.jwt.allow-local-http=true"
        }
)
@EnabledIfSystemProperty(named = "it.postgres.enabled", matches = "true")
abstract class AbstractFinancePostgresIT {

    private static final boolean EXTERNAL = System.getProperty("it.postgres.url") != null;

    private static PostgreSQLContainer<?> container;

    static {
        if (!EXTERNAL) {
            container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("finance_it").withUsername("postgres").withPassword("postgres");
            container.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (EXTERNAL) {
            registry.add("spring.datasource.url",
                    () -> System.getProperty("it.postgres.url") + "?currentSchema=it_finance");
            registry.add("spring.datasource.username",
                    () -> System.getProperty("it.postgres.user", "postgres"));
            registry.add("spring.datasource.password",
                    () -> System.getProperty("it.postgres.pass", "root"));
        } else {
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
