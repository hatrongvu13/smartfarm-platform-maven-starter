package com.htv.smartfarm.identity.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlobalPermissionOwnershipGuardTest {
    @Test
    void permissionTableMustRemainGlobal() throws Exception {
        Path migration = findOne(
                Path.of("src/main/resources/db/migration"),
                "V1__identity_clean_baseline.sql"
        );
        String sql = Files.readString(migration);
        String table = between(
                sql,
                "CREATE TABLE sf_permission",
                ");"
        );
        assertThat(table).doesNotContain("tenant_id");
        assertThat(table).contains("code varchar(80) primary key");
    }

    @Test
    void canonicalProtoExposesPlatformPermissionCrud() throws Exception {
        Path workspace = Path.of("..").toAbsolutePath().normalize();
        List<Path> candidates;
        try (var paths = Files.walk(workspace)) {
            candidates = paths
                    .filter(path -> path.toString().endsWith(".proto"))
                    .filter(path -> {
                        try {
                            String text = Files.readString(path);
                            return text.contains("package smartfarm.identity.v1;")
                                    && text.contains("service IdentityAdministrationService")
                                    && text.contains("service PlatformAuthorizationAdministrationService");
                        } catch (Exception ignored) {
                            return false;
                        }
                    })
                    .toList();
        }
        assertThat(candidates).hasSize(1);
        String proto = Files.readString(candidates.get(0));
        assertThat(proto)
                .contains("rpc CreateGlobalPermission")
                .contains("rpc UpdateGlobalPermission")
                .contains("rpc DeleteGlobalPermission")
                .contains("rpc GetGlobalPermission");
        assertThat(proto)
                .contains("rpc CreatePermission(CreatePermissionRequest)")
                .contains("option deprecated = true;");
    }

    private static Path findOne(Path directory, String name) throws Exception {
        try (var paths = Files.walk(directory)) {
            return paths.filter(path -> path.getFileName().toString().equals(name))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static String between(String value, String start, String end) {
        int from = value.indexOf(start);
        int to = value.indexOf(end, from);
        if (from < 0 || to < 0) throw new IllegalStateException("SQL block not found");
        return value.substring(from, to);
    }
}
