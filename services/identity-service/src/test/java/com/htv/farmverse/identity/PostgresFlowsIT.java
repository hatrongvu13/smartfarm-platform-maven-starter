package com.htv.farmverse.identity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I8 + M4: chạy lại các luồng chính của identity trên PostgreSQL thật, bằng vai trò ứng dụng {@code identity_app}
 * (chỉ DML), migration bằng {@code identity_owner} — đúng như production.
 *
 * <p>Các lớp lồng kế thừa test có sẵn ({@link AuthorizationCodeFlowTest}, {@link TokenLifecycleTest},
 * {@link TotpLoginFlowTest}, {@link RefreshTokenReuseTest}, {@link AccountLockoutTest}), nên mọi kịch bản đó chạy nguyên vẹn, chỉ đổi kho dữ liệu. Bắt được những gì H2 không bắt:
 * migration lệch entity, kiểu cột JSON/{@code bytea} của SAS, ràng buộc, và quyền thiếu của vai trò ứng dụng.
 * Cấu hình ở lớp ngoài áp cho mọi lớp lồng; cùng cấu hình nên dùng chung một context.
 */
class PostgresFlowsIT {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        PostgresTestcontainer.register(registry);
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class Schema {

        @Autowired
        JdbcTemplate jdbc;

        @Autowired
        RegisteredClientRepository clients;

        @Test
        void flywayAppliedAllMigrationsAndHibernateValidatedTheSchema() {
            assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",
                    String.class)).containsExactly("1", "2", "3", "4");
        }

        /** Bảng do Flyway (identity_owner) tạo; ứng dụng chạy bằng identity_app, không phải chủ bảng. */
        @Test
        void applicationRunsAsTheDmlOnlyRoleOnTablesOwnedByTheMigrationRole() {
            assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("identity_app");
            assertThat(jdbc.queryForList("select distinct tableowner from pg_tables where schemaname = 'public'", String.class))
                    .containsExactly("identity_owner");
        }

        /** Client OAuth khai trong cấu hình được ghi vào bảng JDBC của SAS lúc khởi động và đọc lại được. */
        @Test
        void oauthClientsAreProvisionedIntoPostgres() {
            assertThat(clients.findByClientId("farmverse-gateway")).isNotNull();
        }
    }

    @Nested
    class AuthorizationCodeFlow extends AuthorizationCodeFlowTest {
    }

    @Nested
    class TokenLifecycle extends TokenLifecycleTest {
    }

    @Nested
    class TotpLoginFlow extends TotpLoginFlowTest {
    }

    @Nested
    class RefreshTokenReuse extends RefreshTokenReuseTest {
    }

    @Nested
    class AccountLockout extends AccountLockoutTest {
    }
}
