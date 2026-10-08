package com.htv.smartfarm.identity.authorization.application;

import java.util.Locale;
import com.htv.smartfarm.common.exception.ConflictException;
import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.identity.authorization.domain.PermissionEntity;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DynamicAuthorizationAdministrationService {
    private final RoleManagementService management; private final PermissionRepository permissions;
    private final RoleRepository roles; private final RolePermissionRepository grants; private final MembershipRoleRepository assignments;
    public DynamicAuthorizationAdministrationService(RoleManagementService management, PermissionRepository permissions,
            RoleRepository roles, RolePermissionRepository grants, MembershipRoleRepository assignments) {
        this.management=management;this.permissions=permissions;this.roles=roles;this.grants=grants;this.assignments=assignments;
    }
    @Transactional public PermissionEntity createPermission(String code,String resource,String action,String description){
        String c=normalizePermission(code); String r=normalizePart(resource); String a=normalizePart(action);
        if(permissions.existsById(c)) throw new ConflictException("PERMISSION_CODE_ALREADY_EXISTS","Permission code already exists");
        if(permissions.existsByResourceTypeAndAction(r,a)) throw new ConflictException("PERMISSION_ALREADY_EXISTS","Permission already exists for resource and action");
        return permissions.save(new PermissionEntity(c,r,a,description==null||description.isBlank()?null:description.trim()));
    }
    @Transactional public PermissionEntity updatePermission(String code,String resource,String action,String description){
        String c=normalizePermission(code); guardPermission(c); var p=permissions.findById(c).orElseThrow(() -> NotFoundException.entity("Permission",c));
        permissions.findByResourceTypeAndAction(normalizePart(resource),normalizePart(action)).filter(x -> !x.getCode().equals(c)).ifPresent(x -> {throw new ConflictException("PERMISSION_ALREADY_EXISTS","Permission already exists for resource and action");});
        p.updateMetadata(normalizePart(resource),normalizePart(action),description); return p;
    }
    @Transactional public boolean deletePermission(String code){
        String c=normalizePermission(code); guardPermission(c);
        if(!permissions.existsById(c)) return false;
        if(grants.countByIdPermissionCode(c)>0) throw new ConflictException("PERMISSION_IN_USE","Revoke permission from all roles before deleting it");
        permissions.deleteById(c); return true;
    }
    @Transactional public String createRole(String tenant,String code,String name,String actor,String correlation){
        String c=normalizeRole(code); guardPlatform(c); return management.createRole(tenant,c,name,false,actor,correlation);
    }
    @Transactional public void updateRole(String tenant,String code,String name){ var r=role(tenant,code); guardSystem(r.getCode(),r.isSystemRole()); r.rename(name); }
    @Transactional public boolean deleteRole(String tenant,String code){ var r=role(tenant,code); guardSystem(r.getCode(),r.isSystemRole());
        if(assignments.countAssignmentsByRoleIds(java.util.List.of(r.getId()))>0) throw new ConflictException("ROLE_IN_USE","Revoke role from all memberships before deleting it");
        grants.deleteAllByRoleId(r.getId()); roles.delete(r); return true; }
    @Transactional public void grant(String tenant,String role,String permission,String actor,String correlation){guardPlatform(normalizeRole(role));management.grantPermissionToRole(tenant,role,normalizePermission(permission),actor,correlation);}
    @Transactional public void revoke(String tenant,String role,String permission,String actor,String correlation){guardPlatform(normalizeRole(role));management.revokePermissionFromRole(tenant,role,normalizePermission(permission),actor,correlation);}
    private com.htv.smartfarm.identity.authorization.domain.RoleEntity role(String t,String c){String code=normalizeRole(c);return roles.findByTenantIdAndCode(t,code).orElseThrow(() -> NotFoundException.entity("Role",t+":"+code));}
    private static void guardPermission(String c){if("*".equals(c))throw new ConflictException("PROTECTED_PERMISSION","Wildcard permission is protected");}
    private static void guardSystem(String c,boolean system){if(system||"USER".equals(c)||"ADMIN".equals(c)||"SUPERADMIN".equals(c)||"PLATFORM_ADMIN".equals(c))throw new ConflictException("PROTECTED_ROLE","System role cannot be changed or deleted");}
    private static void guardPlatform(String c){if("SUPERADMIN".equals(c)||"PLATFORM_ADMIN".equals(c))throw new ConflictException("PLATFORM_ROLE_DELEGATION_NOT_ALLOWED","Platform roles cannot be managed through tenant administration");}
    private static String normalizePermission(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("permission code is required");return v.trim().toLowerCase(Locale.ROOT);}
    private static String normalizePart(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("permission metadata is required");return v.trim().toLowerCase(Locale.ROOT);}
    private static String normalizeRole(String v){if(v==null||!v.trim().matches("[A-Za-z][A-Za-z0-9_]{1,39}"))throw new IllegalArgumentException("Invalid role code");return v.trim().toUpperCase(Locale.ROOT);}
}
