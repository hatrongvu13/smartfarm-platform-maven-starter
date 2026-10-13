package com.htv.smartfarm.identity.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GlobalPermissionReleaseGateTest {
    @Test
    void platformRpcPoliciesAndTenantBlockingRemainExplicit() throws Exception {
        String root = Files.readString(Path.of("src/main/java/com/htv/smartfarm/identity/config/IdentityGrpcSecurityConfiguration.java"));
        assertThat(root)
                .contains("getGetGlobalPermissionMethod")
                .contains("getCreateGlobalPermissionMethod")
                .contains("getUpdateGlobalPermissionMethod")
                .contains("getDeleteGlobalPermissionMethod")
                .contains("PLATFORM_PERMISSION_READ")
                .contains("PLATFORM_PERMISSION_MANAGE");
        String tenant = Files.readString(Path.of("src/main/java/com/htv/smartfarm/identity/grpc/IdentityAdministrationGrpcService.java"));
        assertThat(count(tenant,"PERMISSION_CATALOG_PLATFORM_OWNED")).isEqualTo(3);
        assertThat(tenant).doesNotContain("dynamicAuthorization.createPermission")
                .doesNotContain("dynamicAuthorization.updatePermission")
                .doesNotContain("dynamicAuthorization.deletePermission");
    }
    private int count(String text,String value){int count=0,index=0;while((index=text.indexOf(value,index))>=0){count++;index+=value.length();}return count;}
}
