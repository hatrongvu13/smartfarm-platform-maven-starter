package com.htv.smartfarm.identity.authorization.application;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.domain.RolePermissionEntity;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.config.IdentityAuthorizationProperties;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdentityAuthorizationSynchronizer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(IdentityAuthorizationSynchronizer.class);
    private final IdentityAuthorizationProperties properties;
    private final PermissionRepository permissions;
    private final TenantRepository tenants;
    private final RoleRepository roles;
    private final RolePermissionRepository grants;
    public IdentityAuthorizationSynchronizer(IdentityAuthorizationProperties properties, PermissionRepository permissions,
            TenantRepository tenants, RoleRepository roles, RolePermissionRepository grants) {
        this.properties=properties; this.permissions=permissions; this.tenants=tenants; this.roles=roles; this.grants=grants;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        syncPermissions(); syncRoles();
    }
    private void syncPermissions() {
        if (!properties.permissionSync().enabled()) return;
        Set<String> codes=new HashSet<>(); Set<String> targets=new HashSet<>();
        for (var d: properties.permissionSync().permissions()) {
            String code=required(d.code(),"permission.code").toLowerCase(Locale.ROOT);
            String resource=required(d.resourceType(),"permission.resourceType").toLowerCase(Locale.ROOT);
            String action=required(d.action(),"permission.action").toLowerCase(Locale.ROOT);
            if (!codes.add(code)) throw new IllegalStateException("Duplicate configured permission code: "+code);
            if (!targets.add(resource+"\u0000"+action)) throw new IllegalStateException("Duplicate configured resource/action: "+resource+":"+action);
            var current=permissions.findById(code).orElse(null);
            if (current==null) { permissions.save(new PermissionEntity(code,resource,action,clean(d.description()))); log.info("permission_sync_created code={}",code); }
            else if (properties.permissionSync().updateExisting()) { current.updateMetadata(resource,action,d.description()); log.info("permission_sync_updated code={}",code); }
        }
    }
    private void syncRoles() {
        if (!properties.roleSync().enabled()) return;
        for (var tenant: tenants.findAll()) for (var d: properties.roleSync().roles()) {
            String code=required(d.code(),"role.code").toUpperCase(Locale.ROOT);
            if ("SUPERADMIN".equals(code)||"PLATFORM_ADMIN".equals(code)) continue;
            var role=roles.findByTenantIdAndCode(tenant.getId(),code).orElseGet(() -> roles.save(
                    new RoleEntity(UUID.randomUUID().toString(),tenant,code,required(d.name(),"role.name"),d.systemRole())));
            role.rename(required(d.name(),"role.name"));
            for (String raw: d.permissionCodes()) {
                String permissionCode=required(raw,"role.permissionCode").toLowerCase(Locale.ROOT);
                var permission=permissions.findById(permissionCode).orElseThrow(() -> new IllegalStateException("Configured permission not found: "+permissionCode));
                if (!grants.existsByIdRoleIdAndIdPermissionCode(role.getId(),permissionCode)) grants.save(new RolePermissionEntity(role,permission));
            }
        }
    }
    private static String required(String v,String f){if(v==null||v.isBlank())throw new IllegalStateException(f+" must not be blank");return v.trim();}
    private static String clean(String v){return v==null||v.isBlank()?null:v.trim();}
}
