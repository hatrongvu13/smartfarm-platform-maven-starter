package com.htv.smartfarm.identity.authorization.application;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    private static final int MAX_BATCH_TARGETS = 100;
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
        validateRequest(
                tenantId,
                subjectId,
                target
        );

        AuthorizationContext authorizationContext =
                loadAuthorizationContext(
                        tenantId,
                        subjectId
                );

        if (!authorizationContext.allowed()) {
            return PermissionDecision.deny(
                    target,
                    authorizationContext.reasonCode()
            );
        }

        return evaluateTarget(
                target,
                authorizationContext.permissionCodes(),
                authorizationContext.farmIds()
        );
    }

    /**
     * Kiểm tra nhiều permission bằng cách chỉ tải account, membership,
     * role, permission và farm scope một lần.
     */
    @Transactional(readOnly = true)
    public List<PermissionDecision> batchCheckPermissions(
            String tenantId,
            String subjectId,
            List<PermissionTarget> targets
    ) {
        requireText(tenantId, "tenantId");
        requireText(subjectId, "subjectId");

        if (targets == null || targets.isEmpty()) {
            return List.of();
        }

        if (targets.size() > MAX_BATCH_TARGETS) {
            throw new IllegalArgumentException(
                    "A maximum of "
                            + MAX_BATCH_TARGETS
                            + " permission targets is allowed"
            );
        }

        /*
         * Validate toàn bộ request trước khi truy vấn dữ liệu để tránh
         * trả về một phần kết quả khi có target không hợp lệ.
         */
        targets.forEach(this::validateTarget);

        AuthorizationContext authorizationContext =
                loadAuthorizationContext(
                        tenantId,
                        subjectId
                );

        if (!authorizationContext.allowed()) {
            return denyAll(
                    targets,
                    authorizationContext.reasonCode()
            );
        }

        return targets.stream()
                .map(target -> evaluateTarget(
                        target,
                        authorizationContext.permissionCodes(),
                        authorizationContext.farmIds()
                ))
                .toList();
    }

    private AuthorizationContext loadAuthorizationContext(
            String tenantId,
            String subjectId
    ) {
        UserAccountEntity account = userAccountRepository
                .findById(subjectId)
                .orElse(null);

        if (account == null) {
            return AuthorizationContext.denied(
                    "ACCOUNT_NOT_FOUND"
            );
        }

        if (!account.isEnabled()) {
            return AuthorizationContext.denied(
                    "ACCOUNT_DISABLED"
            );
        }

        if (!account.isLoginAllowed(clock.instant())) {
            return AuthorizationContext.denied(
                    "ACCOUNT_LOCKED"
            );
        }

        TenantMembershipEntity membership =
                membershipRepository
                        .findByTenantIdAndUserId(
                                tenantId,
                                subjectId
                        )
                        .orElse(null);

        if (membership == null) {
            return AuthorizationContext.denied(
                    "MEMBERSHIP_NOT_FOUND"
            );
        }

        if (!membership.getTenant().isEnabled()) {
            return AuthorizationContext.denied(
                    "TENANT_DISABLED"
            );
        }

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            return AuthorizationContext.denied(
                    membershipReason(
                            membership.getStatus()
                    )
            );
        }

        List<String> roleIds =
                membershipRoleRepository
                        .findRoleIdsByMembershipId(
                                membership.getId()
                        );

        if (roleIds.isEmpty()) {
            return AuthorizationContext.denied(
                    "ROLE_NOT_GRANTED"
            );
        }

        Set<String> permissionCodes =
                rolePermissionRepository
                        .findPermissionCodesByRoleIds(roleIds)
                        .stream()
                        .map(this::normalize)
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableSet()
                        );

        Set<String> farmIds = new HashSet<>(
                membershipFarmRepository
                        .findFarmIdsByMembershipId(
                                membership.getId()
                        )
        );

        return AuthorizationContext.allowed(
                permissionCodes,
                Set.copyOf(farmIds)
        );
    }

    private PermissionDecision evaluateTarget(
            PermissionTarget target,
            Set<String> permissionCodes,
            Set<String> farmIds
    ) {
        validateTarget(target);

        String permissionCode = buildPermissionCode(
                target.resourceType(),
                target.action()
        );

        boolean wildcard = permissionCodes.contains("*");

        if (!wildcard && !permissionCodes.contains(permissionCode)) {
            return PermissionDecision.deny(
                    target,
                    "PERMISSION_NOT_GRANTED"
            );
        }

        // The protected SUPERADMIN wildcard grants every resource inside its authenticated
        // tenant, including farms not explicitly mapped to the root membership.
        if (!wildcard && requiresFarmScope(target)
                && !farmIds.contains(target.resourceId())) {
            return PermissionDecision.deny(
                    target,
                    "RESOURCE_OUT_OF_SCOPE"
            );
        }

        return PermissionDecision.allow(
                target,
                "ALLOWED_BY_ROLE"
        );
    }

    private List<PermissionDecision> denyAll(
            List<PermissionTarget> targets,
            String reasonCode
    ) {
        return targets.stream()
                .map(target -> PermissionDecision.deny(
                        target,
                        reasonCode
                ))
                .toList();
    }

    private boolean requiresFarmScope(
            PermissionTarget target
    ) {
        return FARM_RESOURCE.equalsIgnoreCase(
                target.resourceType()
        )
                && target.resourceId() != null
                && !target.resourceId().isBlank();
    }

    private String buildPermissionCode(
            String resourceType,
            String action
    ) {
        return normalize(resourceType)
                + ":"
                + normalize(action);
    }

    private String membershipReason(
            MembershipStatus status
    ) {
        if (status == null) {
            return "MEMBERSHIP_STATUS_INVALID";
        }

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
        validateTarget(target);
    }

    private void validateTarget(
            PermissionTarget target
    ) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "Permission target must not be null"
            );
        }

        requireText(
                target.resourceType(),
                "resourceType"
        );

        requireText(
                target.action(),
                "action"
        );

        if (FARM_RESOURCE.equalsIgnoreCase(
                target.resourceType()
        )) {
            requireText(
                    target.resourceId(),
                    "resourceId"
            );
        }
    }

    private String normalize(String value) {
        return value
                .trim()
                .toUpperCase(Locale.ROOT);
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

    private record AuthorizationContext(
            boolean allowed,
            String reasonCode,
            Set<String> permissionCodes,
            Set<String> farmIds
    ) {

        private AuthorizationContext {
            permissionCodes = permissionCodes == null
                    ? Set.of()
                    : Set.copyOf(permissionCodes);

            farmIds = farmIds == null
                    ? Set.of()
                    : Set.copyOf(farmIds);
        }

        static AuthorizationContext allowed(
                Set<String> permissionCodes,
                Set<String> farmIds
        ) {
            return new AuthorizationContext(
                    true,
                    "ALLOWED",
                    permissionCodes,
                    farmIds
            );
        }

        static AuthorizationContext denied(
                String reasonCode
        ) {
            return new AuthorizationContext(
                    false,
                    reasonCode,
                    Set.of(),
                    Set.of()
            );
        }
    }
}
