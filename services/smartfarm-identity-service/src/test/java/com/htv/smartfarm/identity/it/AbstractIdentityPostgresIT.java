package com.htv.smartfarm.identity.it;

import com.htv.smartfarm.identity.authorization.application.FarmDirectoryPort;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

/**
 * Reusable base for identity PostgreSQL integration tests. Real PostgreSQL 17, Flyway migrate ->
 * ddl-auto=validate -> context, isolated schema it_identity. EXTERNAL (-Dit.postgres.url) or
 * TESTCONTAINERS mode; enabled with -Dit.postgres.enabled=true.
 *
 * Provides a @Primary test FarmDirectoryPort that recognises a fixed set of "known" farms, so a
 * grant for a known farm is NOT blocked by the production fail-closed fallback. The fail-closed
 * behaviour itself (unknown farm -> denied) is still exercised because the test port returns false
 * for any farm not in that set.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = {com.htv.smartfarm.identity.IdentityApplication.class,
                AbstractIdentityPostgresIT.TestFarmDirectory.class},
        properties = {
                "spring.flyway.enabled=true",
                "spring.flyway.baseline-on-migrate=false",
                "spring.flyway.schemas=it_identity",
                "spring.flyway.default-schema=it_identity",
                "spring.flyway.create-schemas=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.default_schema=it_identity",
                "spring.grpc.server.port=0",
                "smartfarm.identity.mqtt.enabled=false",
                "smartfarm.identity.identity-audience=smartfarm-identity",
                "smartfarm.identity.issuer=http://localhost:8092",
                "smartfarm.identity.private-key-path=classpath:.local/identity-private.pem",
                "smartfarm.identity.public-key-path=classpath:.local/identity-public.pem",
                "smartfarm.identity.bootstrap-password=",
                "smartfarm.security.jwt.issuer=http://localhost:8092",
                "smartfarm.security.jwt.jwk-set-uri=http://localhost:8092/.well-known/jwks.json",
                "smartfarm.security.jwt.audiences=smartfarm-identity",
                "smartfarm.security.jwt.allow-local-http=true"
        }
)
@EnabledIfSystemProperty(named = "it.postgres.enabled", matches = "true")
abstract class AbstractIdentityPostgresIT {

    /** Farms this test treats as existing. Grants for these succeed; anything else is denied. */
    static final Set<String> KNOWN_FARMS = Set.of("farm-A", "farm-B");

    @TestConfiguration
    static class TestFarmDirectory {
        @Bean
        @Primary
        FarmDirectoryPort testFarmDirectoryPort() {
            return (tenantId, farmId) -> KNOWN_FARMS.contains(farmId);
        }
    }

    private static final boolean EXTERNAL = System.getProperty("it.postgres.url") != null;
    private static PostgreSQLContainer<?> container;

    static {
        if (!EXTERNAL) {
            container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("identity_it").withUsername("postgres").withPassword("postgres");
            container.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (EXTERNAL) {
            registry.add("spring.datasource.url",
                    () -> System.getProperty("it.postgres.url") + "?currentSchema=it_identity");
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
