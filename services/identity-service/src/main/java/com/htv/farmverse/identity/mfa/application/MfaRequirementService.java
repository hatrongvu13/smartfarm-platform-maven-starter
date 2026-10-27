package com.htv.farmverse.identity.mfa.application;

import java.util.List;
import java.util.Set;

import com.htv.farmverse.common.exception.NotFoundException;
import com.htv.farmverse.identity.authorization.repository.MembershipRoleRepository;
import com.htv.farmverse.identity.tenant.repository.TenantMembershipRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MfaRequirementService {

    private static final Set<String> REQUIRED_TOTP_ROLES = Set.of(
            "ADMIN", "PLATFORM_ADMIN", "SUPERADMIN"
    );

    private final TenantMembershipRepository memberships;
    private final MembershipRoleRepository membershipRoles;

    @Transactional(readOnly = true)
    public boolean requiresTotp(String tenantId, String userId) {
        var membership = memberships.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> NotFoundException.entity(
                        "TenantMembership", tenantId + ":" + userId));
        List<String> roles = membershipRoles.findRoleCodesByMembershipId(membership.getId());
        return roles.stream().map(String::toUpperCase).anyMatch(REQUIRED_TOTP_ROLES::contains);
    }
}
