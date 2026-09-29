package com.htv.smartfarm.identity.account.application.model;

import java.time.Instant;

import com.htv.smartfarm.identity.tenant.application.model.TenantMembershipData;

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
