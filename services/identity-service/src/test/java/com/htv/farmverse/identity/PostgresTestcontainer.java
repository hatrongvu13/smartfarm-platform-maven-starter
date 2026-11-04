package com.htv.farmverse.identity;

import java.nio.file.Path;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * PostgreSQL 17 thật cho {@code *IT}, dựng bằng CHÍNH {@code infra/postgres-init.sh} (như {@code compose.yaml}):
 * database {@code farmverse_identity}, vai trò {@code identity_owner} (Flyway) và {@code identity_app} (ứng dụng, chỉ DML).
 * Ứng dụng trong test chạy bằng {@code identity_app}, nên quyền thiếu là test đỏ, giống production.
 *
 * <p>Một container cho cả JVM test (static), dừng khi JVM thoát.
 */
final class PostgresTestcontainer {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("../../infra/postgres-init.sh").toAbsolutePath().normalize(), 0755),
                    "/docker-entrypoint-initdb.d/01-farmverse.sh");

    private PostgresTestcontainer() {
    }

    /** Datasource = identity_app, Flyway = identity_owner, schema thật (Flyway + validate, không dùng script test của H2). */
    static void register(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/farmverse_identity";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "identity_app");
        registry.add("spring.datasource.password", () -> "identity-app-dev");
        registry.add("spring.flyway.user", () -> "identity_owner");
        registry.add("spring.flyway.password", () -> "identity-owner-dev");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.sql.init.mode", () -> "never");
        // Bộ đếm đăng nhập thật trên PostgreSQL (M8). Giới hạn cao: các test luồng đăng nhập dùng chung một IP
        // và một database; hành vi giới hạn được kiểm riêng ở LoginRateLimitPostgresIT.
        registry.add("farmverse.identity.rate-limit.store", () -> "jdbc");
        registry.add("farmverse.identity.rate-limit.max-attempts", () -> "1000");
    }
}
