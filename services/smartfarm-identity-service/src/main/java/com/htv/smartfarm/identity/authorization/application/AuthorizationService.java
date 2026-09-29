package com.htv.smartfarm.identity.authorization.application;

import java.time.Clock;
import java.util.List;

import com.htv.smartfarm.identity.account.domain.UserAccountEntity;
import com.htv.smartfarm.identity.account.repository.UserAccountRepository;
import com.htv.smartfarm.identity.authorization.application.model.PermissionDecision;
import com.htv.smartfarm.identity.authorization.application.model.PermissionTarget;
import com.htv.smartfarm.identity.authorization.repository.MembershipFarmRepository;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.authorization.repository.RolePermissionRepository;
import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;
import com.htv.smartfarm.identity.tenant.domain.TenantMembershipEntity;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService {

    private static final String FARM_RESOURCE = "FARM";

    private final UserAccountRepository userAccountRepository;
    private final TenantMembershipRepository membershipRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final MembershipFarmRepository membershipFarmRepository;
    private final Clock clock;

    public AuthorizationService(
            UserAccountRepository userAccountRepository,
            TenantMembershipRepository membershipRepository,
            MembershipRoleRepository membershipRoleRepository,
            RolePermissionRepository rolePermissionRepository,
            MembershipFarmRepository membershipFarmRepository,
            Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
        this.membershipRoleRepository = membershipRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.membershipFarmRepository = membershipFarmRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PermissionDecision checkPermission(
            String tenantId,
            String subjectId,
            PermissionTarget target
    ) {
        validateRequest(tenantId, subjectId, target);

        UserAccountEntity account = userAccountRepository
                .findById(subjectId)
                .orElse(null);

        if (account == null) {
            return PermissionDecision.deny(
                    target,
                    "ACCOUNT_NOT_FOUND"
            );
        }

        if (!account.isEnabled()) {
            return PermissionDecision.deny(
                    target,
                    "ACCOUNT_DISABLED"
            );
        }

        if (!account.isLoginAllowed(clock.instant())) {
            return PermissionDecision.deny(
                    target,
                    "ACCOUNT_LOCKED"
            );
        }

        TenantMembershipEntity membership =
                membershipRepository
                        .findByTenantIdAndUserId(tenantId, subjectId)
                        .orElse(null);

        if (membership == null) {
            return PermissionDecision.deny(
                    target,
                    "MEMBERSHIP_NOT_FOUND"
            );
        }

        if (!membership.getTenant().isEnabled()) {
            return PermissionDecision.deny(
                    target,
                    "TENANT_DISABLED"
            );
        }

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            return PermissionDecision.deny(
                    target,
                    membershipReason(membership.getStatus())
            );
        }

        List<String> roleIds = membershipRoleRepository
                .findRoleIdsByMembershipId(membership.getId());

        if (roleIds.isEmpty()) {
            return PermissionDecision.deny(
                    target,
                    "ROLE_NOT_GRANTED"
            );
        }

        boolean permissionGranted =
                rolePermissionRepository.countGrantedPermission(
                        roleIds,
                        normalize(target.resourceType()),
                        normalize(target.action())
                ) > 0;

        if (!permissionGranted) {
            return PermissionDecision.deny(
                    target,
                    "PERMISSION_NOT_GRANTED"
            );
        }

        if (requiresFarmScope(target)) {
            boolean farmAccessible =
                    membershipFarmRepository
                            .existsByMembershipIdAndFarmId(
                                    membership.getId(),
                                    target.resourceId()
                            );

            if (!farmAccessible) {
                return PermissionDecision.deny(
                        target,
                        "RESOURCE_OUT_OF_SCOPE"
                );
            }
        }

        return PermissionDecision.allow(
                target,
                "ALLOWED_BY_ROLE"
        );
    }

    @Transactional(readOnly = true)
    public List<PermissionDecision> batchCheckPermissions(
            String tenantId,
            String subjectId,
            List<PermissionTarget> targets
    ) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }

        if (targets.size() > 100) {
            throw new IllegalArgumentException(
                    "A maximum of 100 permission targets is allowed"
            );
        }

        return targets.stream()
                .map(target -> checkPermission(
                        tenantId,
                        subjectId,
                        target
                ))
                .toList();
    }

    private boolean requiresFarmScope(
            PermissionTarget target
    ) {
        return FARM_RESOURCE.equalsIgnoreCase(
                target.resourceType()
        ) && target.resourceId() != null
                && !target.resourceId().isBlank();
    }

    private String membershipReason(
            MembershipStatus status
    ) {
        return switch (status) {
            case INVITED -> "MEMBERSHIP_NOT_ACTIVE";
            case SUSPENDED -> "MEMBERSHIP_SUSPENDED";
            case DISABLED -> "MEMBERSHIP_DISABLED";
            case ACTIVE -> "ALLOWED";
        };
    }

    private void validateRequest(
            String tenantId,
            String subjectId,
            PermissionTarget target
    ) {
        requireText(tenantId, "tenantId");
        requireText(subjectId, "subjectId");

        if (target == null) {
            throw new IllegalArgumentException(
                    "Permission target must not be null"
            );
        }

        requireText(target.resourceType(), "resourceType");
        requireText(target.action(), "action");
    }

    private String normalize(String value) {
        return value.trim().toUpperCase();
    }

    private void requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }
}