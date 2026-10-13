package com.htv.smartfarm.identity.authorization.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorizationService {

    private static final int MAX_BATCH_TARGETS = 100;

    private static final String FARM_RESOURCE = "FARM";
    private static final String WILDCARD_PERMISSION = "*";

    private static final String REASON_ALLOWED_BY_ROLE = "ALLOWED_BY_ROLE";
    private static final String REASON_ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
    private static final String REASON_ACCOUNT_DISABLED = "ACCOUNT_DISABLED";
    private static final String REASON_ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    private static final String REASON_MEMBERSHIP_NOT_FOUND = "MEMBERSHIP_NOT_FOUND";
    private static final String REASON_MEMBERSHIP_NOT_ACTIVE = "MEMBERSHIP_NOT_ACTIVE";
    private static final String REASON_MEMBERSHIP_SUSPENDED = "MEMBERSHIP_SUSPENDED";
    private static final String REASON_MEMBERSHIP_DISABLED = "MEMBERSHIP_DISABLED";
    private static final String REASON_MEMBERSHIP_STATUS_INVALID = "MEMBERSHIP_STATUS_INVALID";
    private static final String REASON_TENANT_DISABLED = "TENANT_DISABLED";
    private static final String REASON_ROLE_NOT_GRANTED = "ROLE_NOT_GRANTED";
    private static final String REASON_PERMISSION_NOT_GRANTED = "PERMISSION_NOT_GRANTED";
    private static final String REASON_RESOURCE_OUT_OF_SCOPE = "RESOURCE_OUT_OF_SCOPE";

    private final UserAccountRepository userAccountRepository;
    private final TenantMembershipRepository membershipRepository;
    private final MembershipRoleRepository membershipRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final MembershipFarmRepository membershipFarmRepository;
    private final Clock clock;

    public PermissionDecision checkPermission(
            String tenantId,
            String subjectId,
            PermissionTarget target) {

        validateRequest(tenantId, subjectId, target);

        log.debug(
                "Checking permission: tenantId={}, subjectId={}, resourceType={}, action={}, "
                        + "resourceIdPresent={}",
                tenantId,
                subjectId,
                target.resourceType(),
                target.action(),
                hasText(target.resourceId()));

        AuthorizationContext context = loadAuthorizationContext(
                tenantId,
                subjectId);

        if (!context.allowed()) {
            logDeniedContext(
                    tenantId,
                    subjectId,
                    context.reasonCode());

            return PermissionDecision.deny(
                    target,
                    context.reasonCode());
        }

        PermissionDecision decision = evaluateTarget(target, context);

        logDecision(
                tenantId,
                subjectId,
                target,
                decision);

        return decision;
    }

    /**
     * Kiểm tra nhiều permission bằng cách chỉ tải account, membership,
     * role, permission và farm scope một lần.
     */
    public List<PermissionDecision> batchCheckPermissions(
            String tenantId,
            String subjectId,
            List<PermissionTarget> targets) {

        requireText(tenantId, "tenantId");
        requireText(subjectId, "subjectId");

        if (targets == null || targets.isEmpty()) {
            log.debug(
                    "Skipping batch permission check because targets are empty: "
                            + "tenantId={}, subjectId={}",
                    tenantId,
                    subjectId);

            return List.of();
        }

        validateBatchSize(targets.size());

        /*
         * Validate toàn bộ request trước khi truy vấn dữ liệu để tránh
         * trả về một phần kết quả khi có target không hợp lệ.
         */
        targets.forEach(this::validateTarget);

        log.debug(
                "Checking permission batch: tenantId={}, subjectId={}, targetCount={}",
                tenantId,
                subjectId,
                targets.size());

        AuthorizationContext context = loadAuthorizationContext(
                tenantId,
                subjectId);

        if (!context.allowed()) {
            logDeniedContext(
                    tenantId,
                    subjectId,
                    context.reasonCode());

            return denyAll(
                    targets,
                    context.reasonCode());
        }

        List<PermissionDecision> decisions = targets.stream()
                .map(target -> evaluateTarget(target, context))
                .toList();

        long allowedCount = decisions.stream()
                .filter(PermissionDecision::allowed)
                .count();

        log.debug(
                "Batch permission check completed: tenantId={}, subjectId={}, "
                        + "targetCount={}, allowedCount={}, deniedCount={}",
                tenantId,
                subjectId,
                targets.size(),
                allowedCount,
                decisions.size() - allowedCount);

        return decisions;
    }

    private AuthorizationContext loadAuthorizationContext(
            String tenantId,
            String subjectId) {

        UserAccountEntity account = userAccountRepository
                .findById(subjectId)
                .orElse(null);

        if (account == null) {
            return AuthorizationContext.denied(
                    REASON_ACCOUNT_NOT_FOUND);
        }

        if (!account.isEnabled()) {
            return AuthorizationContext.denied(
                    REASON_ACCOUNT_DISABLED);
        }

        Instant now = clock.instant();

        if (!account.isLoginAllowed(now)) {
            return AuthorizationContext.denied(
                    REASON_ACCOUNT_LOCKED);
        }

        TenantMembershipEntity membership = membershipRepository
                .findByTenantIdAndUserId(tenantId, subjectId)
                .orElse(null);

        if (membership == null) {
            return AuthorizationContext.denied(
                    REASON_MEMBERSHIP_NOT_FOUND);
        }

        if (!membership.getTenant().isEnabled()) {
            return AuthorizationContext.denied(
                    REASON_TENANT_DISABLED);
        }

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            return AuthorizationContext.denied(
                    membershipReason(membership.getStatus()));
        }

        List<String> roleIds = membershipRoleRepository
                .findRoleIdsByMembershipId(membership.getId());

        if (roleIds.isEmpty()) {
            return AuthorizationContext.denied(
                    REASON_ROLE_NOT_GRANTED);
        }

        Set<String> permissionCodes = rolePermissionRepository
                .findPermissionCodesByRoleIds(roleIds)
                .stream()
                .filter(AuthorizationService::hasText)
                .map(AuthorizationService::normalize)
                .collect(Collectors.toUnmodifiableSet());

        Set<String> farmIds = Set.copyOf(
                membershipFarmRepository.findFarmIdsByMembershipId(
                        membership.getId()));

        log.debug(
                "Authorization context loaded: tenantId={}, subjectId={}, membershipId={}, "
                        + "roleCount={}, permissionCount={}, farmScopeCount={}, wildcard={}",
                tenantId,
                subjectId,
                membership.getId(),
                roleIds.size(),
                permissionCodes.size(),
                farmIds.size(),
                permissionCodes.contains(WILDCARD_PERMISSION));

        return AuthorizationContext.allowed(
                permissionCodes,
                farmIds);
    }

    private PermissionDecision evaluateTarget(
            PermissionTarget target,
            AuthorizationContext context) {

        String permissionCode = buildPermissionCode(
                target.resourceType(),
                target.action());

        boolean wildcardGranted = context.permissionCodes()
                .contains(WILDCARD_PERMISSION);

        if (!wildcardGranted
                && !context.permissionCodes().contains(permissionCode)) {

            return PermissionDecision.deny(
                    target,
                    REASON_PERMISSION_NOT_GRANTED);
        }

        /*
         * Wildcard được bảo vệ dành cho SUPERADMIN cho phép truy cập mọi
         * resource trong tenant đã xác thực, kể cả farm chưa được gán
         * trực tiếp vào membership.
         */
        if (!wildcardGranted
                && requiresFarmScope(target)
                && !context.farmIds().contains(target.resourceId())) {

            return PermissionDecision.deny(
                    target,
                    REASON_RESOURCE_OUT_OF_SCOPE);
        }

        return PermissionDecision.allow(
                target,
                REASON_ALLOWED_BY_ROLE);
    }

    private List<PermissionDecision> denyAll(
            List<PermissionTarget> targets,
            String reasonCode) {

        return targets.stream()
                .map(target -> PermissionDecision.deny(
                        target,
                        reasonCode))
                .toList();
    }

    private void logDecision(
            String tenantId,
            String subjectId,
            PermissionTarget target,
            PermissionDecision decision) {

        if (decision.allowed()) {
            log.debug(
                    "Permission allowed: tenantId={}, subjectId={}, resourceType={}, "
                            + "action={}, resourceIdPresent={}, reasonCode={}",
                    tenantId,
                    subjectId,
                    target.resourceType(),
                    target.action(),
                    hasText(target.resourceId()),
                    decision.reasonCode());

            return;
        }

        log.debug(
                "Permission denied: tenantId={}, subjectId={}, resourceType={}, "
                        + "action={}, resourceIdPresent={}, reasonCode={}",
                tenantId,
                subjectId,
                target.resourceType(),
                target.action(),
                hasText(target.resourceId()),
                decision.reasonCode());
    }

    private void logDeniedContext(
            String tenantId,
            String subjectId,
            String reasonCode) {

        log.debug(
                "Authorization context denied: tenantId={}, subjectId={}, reasonCode={}",
                tenantId,
                subjectId,
                reasonCode);
    }

    private static boolean requiresFarmScope(PermissionTarget target) {
        return FARM_RESOURCE.equalsIgnoreCase(target.resourceType())
                && hasText(target.resourceId());
    }

    private static String buildPermissionCode(
            String resourceType,
            String action) {

        return normalize(resourceType)
                + ":"
                + normalize(action);
    }

    private static String membershipReason(MembershipStatus status) {
        if (status == null) {
            return REASON_MEMBERSHIP_STATUS_INVALID;
        }

        return switch (status) {
            case INVITED -> REASON_MEMBERSHIP_NOT_ACTIVE;
            case SUSPENDED -> REASON_MEMBERSHIP_SUSPENDED;
            case DISABLED -> REASON_MEMBERSHIP_DISABLED;
            case ACTIVE -> throw new IllegalStateException(
                    "Active membership must not be mapped to a denial reason");
        };
    }

    private void validateRequest(
            String tenantId,
            String subjectId,
            PermissionTarget target) {

        requireText(tenantId, "tenantId");
        requireText(subjectId, "subjectId");
        validateTarget(target);
    }

    private static void validateBatchSize(int targetCount) {
        if (targetCount > MAX_BATCH_TARGETS) {
            throw new IllegalArgumentException(
                    "A maximum of "
                            + MAX_BATCH_TARGETS
                            + " permission targets is allowed");
        }
    }

    private void validateTarget(PermissionTarget target) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "Permission target must not be null");
        }

        requireText(target.resourceType(), "resourceType");
        requireText(target.action(), "action");

        if (FARM_RESOURCE.equalsIgnoreCase(target.resourceType())) {
            requireText(target.resourceId(), "resourceId");
        }
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireText(
            String value,
            String field) {

        if (!hasText(value)) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record AuthorizationContext(
            boolean allowed,
            String reasonCode,
            Set<String> permissionCodes,
            Set<String> farmIds) {

        private AuthorizationContext {
            requireText(reasonCode, "reasonCode");

            permissionCodes = permissionCodes == null
                    ? Set.of()
                    : Set.copyOf(permissionCodes);

            farmIds = farmIds == null
                    ? Set.of()
                    : Set.copyOf(farmIds);
        }

        private static AuthorizationContext allowed(
                Set<String> permissionCodes,
                Set<String> farmIds) {

            return new AuthorizationContext(
                    true,
                    REASON_ALLOWED_BY_ROLE,
                    permissionCodes,
                    farmIds);
        }

        private static AuthorizationContext denied(String reasonCode) {
            return new AuthorizationContext(
                    false,
                    reasonCode,
                    Set.of(),
                    Set.of());
        }
    }
}