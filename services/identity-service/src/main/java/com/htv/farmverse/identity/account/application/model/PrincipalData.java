package com.htv.farmverse.identity.account.application.model;

import java.time.Instant;

import com.htv.farmverse.identity.tenant.application.model.TenantMembershipData;

public record PrincipalData(
        String subjectId,
        PrincipalProfileData profile,
        boolean active,
        TenantMembershipData membership,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
