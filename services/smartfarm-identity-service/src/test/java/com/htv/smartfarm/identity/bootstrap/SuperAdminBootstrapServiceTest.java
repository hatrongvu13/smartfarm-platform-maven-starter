package com.htv.smartfarm.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.htv.smartfarm.identity.account.application.AccountService;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.PermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.authorization.repository.RoleRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantRepository;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SuperAdminBootstrapServiceTest {

    private final IdentityBootstrapLockRepository lockRepository =
            mock(IdentityBootstrapLockRepository.class);
    private final MembershipRoleRepository membershipRoleRepository =
            mock(MembershipRoleRepository.class);
    private final SuperAdminBootstrapService service =
            new SuperAdminBootstrapService(
                    mock(AccountService.class),
                    lockRepository,
                    mock(UserAccountRepository.class),
                    mock(TenantRepository.class),
                    mock(TenantMembershipRepository.class),
                    mock(RoleRepository.class),
                    membershipRoleRepository,
                    mock(PermissionRepository.class),
                    mock(RolePermissionRepository.class)
            );

    @Test
    void obtainsBootstrapLockBeforeCheckingWhetherBootstrapIsClosed() {
        when(lockRepository.findByIdForUpdate(
                IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID
        )).thenReturn(java.util.Optional.of(
                new IdentityBootstrapLockEntity(
                        IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID
                )
        ));
        when(membershipRoleRepository.countByRoleCode(
                SuperAdminBootstrapService.SUPERADMIN
        )).thenReturn(1L);

        assertThatThrownBy(() -> service.createFirstSuperAdmin(
                "admin@example.test",
                "test-password"
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        var calls = inOrder(lockRepository, membershipRoleRepository);
        calls.verify(lockRepository).findByIdForUpdate(
                IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID
        );
        calls.verify(membershipRoleRepository).countByRoleCode(
                SuperAdminBootstrapService.SUPERADMIN
        );
    }
}
