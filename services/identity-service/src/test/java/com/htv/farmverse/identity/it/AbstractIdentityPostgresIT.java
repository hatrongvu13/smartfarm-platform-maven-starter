package com.htv.farmverse.identity.it;

import com.htv.farmverse.identity.authorization.application.FarmDirectoryPort;

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
 * <p>
 * Provides a @Primary test FarmDirectoryPort that recognises a fixed set of "known" farms, so a
 * grant for a known farm is NOT blocked by the production fail-closed fallback. The fail-closed
 * behaviour itself (unknown farm -> denied) is still exercised because the test port returns false
 * for any farm not in that set.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = {com.htv.farmverse.identity.IdentityApplication.class,
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
                "farmverse.identity.mqtt.enabled=false",
                "farmverse.identity.identity-audience=farmverse-identity",
                "farmverse.identity.issuer=http://localhost:8092",
                "farmverse.identity.private-key-path=classpath:.local/identity-private.pem",
                "farmverse.identity.public-key-path=classpath:.local/identity-public.pem",
                "farmverse.security.jwt.issuer=http://localhost:8092",
                "farmverse.security.jwt.jwk-set-uri=http://localhost:8092/.well-known/jwks.json",
                "farmverse.security.jwt.audiences=farmverse-identity",
                "farmverse.security.jwt.allow-local-http=true"
        }
)
abstract class AbstractIdentityPostgresIT {

    /**
     * Farms this test treats as existing. Grants for these succeed; anything else is denied.
     */
    static final Set<String> KNOWN_FARMS = Set.of("farm-A", "farm-B");

    @TestConfiguration
    static class TestFarmDirectory {
        @Bean
        @Primary
        FarmDirectoryPort testFarmDirectoryPort() {
            return (tenantId, farmId) -> KNOWN_FARMS.contains(farmId);
        }
    }

    private static final boolean EXTERNAL = setting("it.postgres.url", "IDENTITY_IT_POSTGRES_URL") != null;
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("it.postgres.enabled", "false")
    );
    private static PostgreSQLContainer<?> container;

    static {
        if (ENABLED && !EXTERNAL) {
            container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("identity_it").withUsername("postgres").withPassword("postgres");
            container.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (!ENABLED) {
            throw new IllegalStateException(
                    "PostgreSQL integration tests require -Dit.postgres.enabled=true"
            );
        }
        if (EXTERNAL) {
            registry.add("spring.datasource.url",
                    () -> withTestSchema(setting("it.postgres.url", "IDENTITY_IT_POSTGRES_URL")));
            registry.add("spring.datasource.username",
                    () -> requiredSetting("it.postgres.user", "IDENTITY_IT_POSTGRES_USER"));
            registry.add("spring.datasource.password",
                    () -> requiredSetting("it.postgres.pass", "IDENTITY_IT_POSTGRES_PASSWORD"));
        } else {
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private static String setting(String property, String environmentVariable) {
        String value = System.getProperty(property);
        return value == null || value.isBlank()
                ? System.getenv(environmentVariable)
                : value;
    }

    private static String requiredSetting(
            String property,
            String environmentVariable
    ) {
        String value = setting(property, environmentVariable);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "External PostgreSQL integration tests require " + environmentVariable
            );
        }
        return value;
    }

    private static String withTestSchema(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "External PostgreSQL integration tests require IDENTITY_IT_POSTGRES_URL"
            );
        }
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=it_identity";
    }
}
