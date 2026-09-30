package com.htv.smartfarm.identity.administration.application.model;

import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;

public record CreatedTenantUser(
        String subjectId,
        String membershipId,
        String tenantId,
        MembershipStatus membershipStatus,
        boolean existingAccount
) {
}