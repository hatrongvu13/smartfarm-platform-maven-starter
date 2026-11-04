package com.htv.farmverse.identity;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL thật cho {@code *IT} (Testcontainers, cần Docker). {@link ServiceConnection} nối datasource vào container,
 * thay cho H2 của profile test. Cùng phiên bản image với {@code compose.yaml}.
 *
 * <p>Dùng kèm {@code spring.flyway.enabled=true}, {@code spring.jpa.hibernate.ddl-auto=validate},
 * {@code spring.sql.init.mode=never}: Flyway chạy migration thật, Hibernate chỉ kiểm, không chạy
 * {@code oauth2-test-schema.sql} — đúng như production, nên migration lệch entity là test đỏ.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
