package com.htv.smartfarm.identity.authorization.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.htv.smartfarm.identity.authorization.domain.RoleEntity;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.config.IdentityAuthorizationProperties;
import com.htv.smartfarm.identity.tenant.domain.TenantEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class IdentityAuthorizationSynchronizerTest {

    @Test
    void doesNotOverwriteUserManagedRoleWithConfiguredSystemRole() {
        var properties = new IdentityAuthorizationProperties(
                new IdentityAuthorizationProperties.PermissionSync(false, false, List.of()),
                new IdentityAuthorizationProperties.RoleSync(
                        true,
                        List.of(new IdentityAuthorizationProperties.RoleDefinition(
                                "ADMIN",
                                "Managed Admin",
                                true,
                                List.of("identity:user:read")
                        ))
                )
        );
        var permissions = mock(PermissionRepository.class);
        var tenants = mock(TenantRepository.class);
        var roles = mock(RoleRepository.class);
        var grants = mock(RolePermissionRepository.class);
        var tenant = new TenantEntity("tenant-id", "tenant", "Tenant");
        var userManagedRole = mock(RoleEntity.class);

        when(tenants.findAll()).thenReturn(List.of(tenant));
        when(roles.findByTenantIdAndCode("tenant-id", "ADMIN"))
                .thenReturn(Optional.of(userManagedRole));
        when(userManagedRole.isSystemRole()).thenReturn(false);

        var synchronizer = new IdentityAuthorizationSynchronizer(
                properties,
                permissions,
                tenants,
                roles,
                grants
        );
        synchronizer.run(mock(ApplicationArguments.class));

        verify(userManagedRole, never()).rename("Managed Admin");
        verify(grants, never()).existsByIdRoleIdAndIdPermissionCode(
                userManagedRole.getId(),
                "identity:user:read"
        );
    }
}
