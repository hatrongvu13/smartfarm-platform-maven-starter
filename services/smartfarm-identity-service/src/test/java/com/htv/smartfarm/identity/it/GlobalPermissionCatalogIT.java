package com.htv.smartfarm.identity.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.identity.authorization.application.GlobalPermissionCatalogAdministrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfSystemProperty(named = "it.postgres.enabled", matches = "true")
class GlobalPermissionCatalogIT extends AbstractIdentityPostgresIT {
    @Autowired private GlobalPermissionCatalogAdministrationService service;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createPersistsPermissionAndLifecycleOutboxEvent() {
        String code = code("create");
        var value = service.create(code, resource(code), action(code),
                "Created by integration test", "actor", "correlation");

        assertThat(value.getCode()).isEqualTo(code);
        assertThat(count("select count(*) from sf_permission where code=?", code))
                .isEqualTo(1);
        assertThat(count("select count(*) from sf_identity_outbox where event_type=? and aggregate_id=?",
                "identity.permission.created", code)).isEqualTo(1);
    }

    @Test
    void duplicateCodeAndResourceActionAreRejected() {
        String code = code("duplicate");
        service.create(code, resource(code), action(code), null, "actor", "c1");
        assertThatThrownBy(() -> service.create(code, resource(code), action(code),
                null, "actor", "c2")).isInstanceOf(ConflictException.class);
        String another = resource(code) + ":other";
        assertThatThrownBy(() -> service.create(another, resource(code), action(code),
                null, "actor", "c3")).isInstanceOf(ConflictException.class);
    }

    @Test
    void staleExpectedVersionIsRejected() {
        String code = code("version");
        var created = service.create(code, resource(code), action(code), null,
                "actor", "correlation");
        long stale = created.getVersion() + 99;
        assertThatThrownBy(() -> service.update(code, resource(code), action(code),
                "changed", stale, "actor", "correlation"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void referencedPermissionCannotBeDeleted() {
        String suffix = Long.toString(System.nanoTime(), 36);
        String tenant = "tenant-perm-" + suffix;
        String role = "role-perm-" + suffix;
        String code = code("inuse" + suffix);
        seedTenant(tenant, suffix);
        service.create(code, resource(code), action(code), null, "actor", "c1");
        jdbc.update("insert into sf_role(id,tenant_id,code,name,role_type,system_owned,created_at,updated_at,entity_version) values (?,?,?,?,?,?,now(),now(),0)",
                role, tenant, "R_" + suffix.toUpperCase(), "Role", "TENANT", false);
        jdbc.update("insert into sf_role_permission(role_id,permission_code) values (?,?)", role, code);

        assertThatThrownBy(() -> service.delete(code, null, "actor", "c2"))
                .isInstanceOf(ConflictException.class);
        assertThat(count("select count(*) from sf_permission where code=?", code))
                .isEqualTo(1);
    }

    @Test
    void updateInvalidatesEveryAffectedTenantAndWritesEvents() {
        String suffix = Long.toString(System.nanoTime(), 36);
        String code = code("multi" + suffix);
        service.create(code, resource(code), action(code), null, "actor", "c1");
        String a = "tenant-a-" + suffix;
        String b = "tenant-b-" + suffix;
        seedGrant(a, "role-a-" + suffix, code, suffix + "a");
        seedGrant(b, "role-b-" + suffix, code, suffix + "b");

        service.update(code, resource(code), action(code), "updated",
                null, "actor", "correlation-update");

        assertThat(version(a)).isEqualTo(1);
        assertThat(version(b)).isEqualTo(1);
        assertThat(count("select count(*) from sf_identity_outbox where event_type=? and aggregate_id=?",
                "identity.permission.updated", code)).isEqualTo(1);
        assertThat(count("select count(*) from sf_identity_outbox where event_type=? and tenant_id in (?,?)",
                "identity.authorization.invalidated", a, b)).isEqualTo(2);
    }

    private void seedGrant(String tenant,String role,String permission,String suffix){
        seedTenant(tenant,suffix);
        jdbc.update("insert into sf_role(id,tenant_id,code,name,role_type,system_owned,created_at,updated_at,entity_version) values (?,?,?,?,?,?,now(),now(),0)",
                role,tenant,"R_"+suffix.toUpperCase(),"Role","TENANT",false);
        jdbc.update("insert into sf_role_permission(role_id,permission_code) values (?,?)",role,permission);
    }
    private void seedTenant(String tenant,String suffix){
        jdbc.update("insert into sf_tenant(id,code,name,enabled,created_at,updated_at,entity_version) values (?,?,?,true,now(),now(),0)",
                tenant,"code-"+suffix,"Tenant "+suffix);
        jdbc.update("insert into sf_authorization_tenant_version(tenant_id,version_value,updated_at,row_version) values (?,0,now(),0)",tenant);
    }
    private long version(String tenant){return jdbc.queryForObject("select version_value from sf_authorization_tenant_version where tenant_id=?",Long.class,tenant);}
    private long count(String sql,Object...args){return jdbc.queryForObject(sql,Long.class,args);}
    private String code(String marker){String value=(marker+Long.toString(System.nanoTime(),36)).replaceAll("[^a-z0-9]","");return "it"+value+":read";}
    private String resource(String code){return code.substring(0,code.indexOf(':'));}
    private String action(String code){return code.substring(code.indexOf(':')+1);}
}
