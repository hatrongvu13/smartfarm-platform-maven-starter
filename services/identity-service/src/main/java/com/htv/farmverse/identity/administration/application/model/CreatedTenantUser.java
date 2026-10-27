package com.htv.farmverse.identity.administration.application.model;

import com.htv.farmverse.identity.tenant.domain.MembershipStatus;

public record CreatedTenantUser(
        String subjectId,
        String membershipId,
        String tenantId,
        MembershipStatus membershipStatus,
        boolean existingAccount
) {
}