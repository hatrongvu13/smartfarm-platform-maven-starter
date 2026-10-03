package com.htv.smartfarm.inventory.it;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Reusable base for inventory PostgreSQL integration tests. Real PostgreSQL 17 (compose/prod),
 * never H2. Flyway migrate -> Hibernate ddl-auto=validate -> context -> test, in an isolated
 * schema (it_inventory). EXTERNAL mode (-Dit.postgres.url, no Docker) or TESTCONTAINERS mode
 * (Docker). Enabled only with -Dit.postgres.enabled=true. See AbstractFinancePostgresIT for the
 * mode details — this mirrors it for the inventory module.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=true",
                "spring.flyway.baseline-on-migrate=false",
                "spring.flyway.schemas=it_inventory",
                "spring.flyway.default-schema=it_inventory",
                "spring.flyway.create-schemas=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.default_schema=it_inventory",
                "spring.grpc.server.port=0",
                "smartfarm.inventory.mqtt.enabled=false",
                "smartfarm.security.jwt.issuer=http://localhost:8092",
                "smartfarm.security.jwt.jwk-set-uri=http://localhost:8092/.well-known/jwks.json",
                "smartfarm.security.jwt.audiences=smartfarm-inventory",
                "smartfarm.security.jwt.allow-local-http=true"
        }
)
@EnabledIfSystemProperty(named = "it.postgres.enabled", matches = "true")
abstract class AbstractInventoryPostgresIT {

    private static final boolean EXTERNAL = System.getProperty("it.postgres.url") != null;
    private static PostgreSQLContainer<?> container;

    static {
        if (!EXTERNAL) {
            container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("inventory_it").withUsername("postgres").withPassword("postgres");
            container.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (EXTERNAL) {
            registry.add("spring.datasource.url",
                    () -> System.getProperty("it.postgres.url") + "?currentSchema=it_inventory");
            registry.add("spring.datasource.username", () -> System.getProperty("it.postgres.user", "postgres"));
            registry.add("spring.datasource.password", () -> System.getProperty("it.postgres.pass", "root"));
        } else {
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
