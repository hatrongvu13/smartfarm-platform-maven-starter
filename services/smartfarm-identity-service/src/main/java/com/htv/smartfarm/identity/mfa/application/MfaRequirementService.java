package com.htv.smartfarm.identity.mfa.application;

import java.util.List;
import java.util.Set;

import com.htv.smartfarm.common.exception.NotFoundException;
import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import com.htv.smartfarm.identity.tenant.repository.TenantMembershipRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaRequirementService {

    private static final Set<String> REQUIRED_TOTP_ROLES = Set.of(
            "ADMIN", "PLATFORM_ADMIN", "SUPERADMIN"
    );

    private final TenantMembershipRepository memberships;
    private final MembershipRoleRepository membershipRoles;

    public MfaRequirementService(TenantMembershipRepository memberships,
            MembershipRoleRepository membershipRoles) {
        this.memberships = memberships;
        this.membershipRoles = membershipRoles;
    }

    @Transactional(readOnly = true)
    public boolean requiresTotp(String tenantId, String userId) {
        var membership = memberships.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> NotFoundException.entity(
                        "TenantMembership", tenantId + ":" + userId));
        List<String> roles = membershipRoles.findRoleCodesByMembershipId(membership.getId());
        return roles.stream().map(String::toUpperCase).anyMatch(REQUIRED_TOTP_ROLES::contains);
    }
}
