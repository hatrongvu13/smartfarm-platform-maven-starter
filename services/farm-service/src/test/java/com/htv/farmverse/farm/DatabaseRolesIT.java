package com.htv.farmverse.farm;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M4: kiểm chính file {@code infra/postgres-init.sh} trên PostgreSQL 17 thật — đúng file mà {@code compose.yaml} chạy.
 * Không dùng Spring: đây là kiểm phân quyền của database, độc lập ứng dụng.
 */
class DatabaseRolesIT {

    static final Path SCRIPT = Path.of("../../infra/postgres-init.sh").toAbsolutePath().normalize();

    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17-alpine")
            .withCopyFileToContainer(MountableFile.forHostPath(SCRIPT, 0755), "/docker-entrypoint-initdb.d/01-farmverse.sh");

    static {
        PG.start();
    }

    @AfterAll
    static void stop() {
        PG.stop();
    }

    static Connection connect(String db, String user, String password) throws SQLException {
        String url = "jdbc:postgresql://" + PG.getHost() + ":" + PG.getFirstMappedPort() + "/" + db;
        return DriverManager.getConnection(url, user, password);
    }

    static Connection superuser(String db) throws SQLException {
        return connect(db, PG.getUsername(), PG.getPassword());
    }

    static void sql(Connection c, String statement) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(statement);
        }
    }

    @Test
    void eachServiceReachesOnlyItsOwnDatabase() throws SQLException {
        connect("farmverse_farm", "farm_app", "farm-app-dev").close();
        connect("farmverse_identity", "identity_app", "identity-app-dev").close();

        assertThatThrownBy(() -> connect("farmverse_identity", "farm_app", "farm-app-dev"))
                .hasMessageContaining("permission denied for database");
        assertThatThrownBy(() -> connect("farmverse_identity", "farm_owner", "farm-owner-dev"))
                .hasMessageContaining("permission denied for database");
        assertThatThrownBy(() -> connect("farmverse_farm", "identity_app", "identity-app-dev"))
                .hasMessageContaining("permission denied for database");
    }

    /** Owner tạo bảng (như Flyway); app đọc ghi được ngay nhờ default privileges, nhưng không đổi được cấu trúc. */
    @Test
    void appRoleHasDmlOnlyOnTablesTheOwnerCreates() throws SQLException {
        try (Connection owner = connect("farmverse_farm", "farm_owner", "farm-owner-dev")) {
            sql(owner, "CREATE TABLE roles_probe (id bigserial PRIMARY KEY, v text)");
        }
        try (Connection app = connect("farmverse_farm", "farm_app", "farm-app-dev")) {
            sql(app, "INSERT INTO roles_probe (v) VALUES ('ok')");   // gồm cả nextval của sequence
            sql(app, "UPDATE roles_probe SET v = 'ok2'");
            sql(app, "DELETE FROM roles_probe");
            assertThatThrownBy(() -> sql(app, "DROP TABLE roles_probe")).hasMessageContaining("must be owner");
            assertThatThrownBy(() -> sql(app, "ALTER TABLE roles_probe ADD COLUMN x int")).hasMessageContaining("must be owner");
            assertThatThrownBy(() -> sql(app, "CREATE TABLE sneaky (id int)")).hasMessageContaining("permission denied for schema public");
        }
    }

    /** Chạy lại script trên volume cũ: bảng do postgres tạo trước M4 được chuyển cho owner và app dùng được. */
    @Test
    void rerunningTheScriptAdoptsTablesCreatedBeforeM4() throws Exception {
        try (Connection su = superuser("farmverse_farm")) {
            sql(su, "CREATE TABLE legacy_probe (id int)");
        }

        var rerun = PG.execInContainer("bash", "/docker-entrypoint-initdb.d/01-farmverse.sh");
        assertThat(rerun.getExitCode()).as(rerun.getStderr()).isZero();

        try (Connection su = superuser("farmverse_farm");
             ResultSet rs = su.createStatement().executeQuery(
                     "SELECT tableowner FROM pg_tables WHERE tablename = 'legacy_probe'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("farm_owner");
        }
        try (Connection app = connect("farmverse_farm", "farm_app", "farm-app-dev")) {
            sql(app, "INSERT INTO legacy_probe VALUES (1)");
        }
    }

    @Test
    void serviceRolesAreNotPrivileged() throws SQLException {
        try (Connection su = superuser("postgres");
             ResultSet rs = su.createStatement().executeQuery(
                     "SELECT count(*) FROM pg_roles WHERE rolname IN ('farm_owner','farm_app','identity_owner','identity_app') "
                             + "AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(4);
        }
    }
}
