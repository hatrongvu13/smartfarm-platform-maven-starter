package com.htv.farmverse.identity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I8: chạy lại các luồng chính của identity trên PostgreSQL thật thay cho H2.
 *
 * <p>Các lớp lồng kế thừa test có sẵn ({@link AuthorizationCodeFlowTest}, {@link TokenLifecycleTest},
 * {@link TotpLoginFlowTest}), nên mọi kịch bản đó chạy nguyên vẹn, chỉ đổi kho dữ liệu. Bắt được những gì H2 không bắt:
 * migration lệch entity, kiểu cột JSON/{@code bytea} của SAS, ràng buộc và khóa của PostgreSQL.
 * Mọi lớp dùng chung cấu hình nên dùng chung một context và một container.
 */
class PostgresFlowsIT {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @Import(PostgresTestcontainer.class)
    @TestPropertySource(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
            "spring.sql.init.mode=never"})
    class Schema {

        @Autowired
        JdbcTemplate jdbc;

        @Autowired
        RegisteredClientRepository clients;

        @Test
        void flywayAppliedBothBaselinesAndHibernateValidatedTheSchema() {
            assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                    String.class)).containsExactly("1", "2");
        }

        /** Client OAuth khai trong cấu hình được ghi vào bảng JDBC của SAS lúc khởi động và đọc lại được. */
        @Test
        void oauthClientsAreProvisionedIntoPostgres() {
            assertThat(clients.findByClientId("farmverse-gateway")).isNotNull();
        }
    }

    @Nested
    @Import(PostgresTestcontainer.class)
    @TestPropertySource(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
            "spring.sql.init.mode=never"})
    class AuthorizationCodeFlow extends AuthorizationCodeFlowTest {
    }

    @Nested
    @Import(PostgresTestcontainer.class)
    @TestPropertySource(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
            "spring.sql.init.mode=never"})
    class TokenLifecycle extends TokenLifecycleTest {
    }

    @Nested
    @Import(PostgresTestcontainer.class)
    @TestPropertySource(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
            "spring.sql.init.mode=never"})
    class TotpLoginFlow extends TotpLoginFlowTest {
    }
}
