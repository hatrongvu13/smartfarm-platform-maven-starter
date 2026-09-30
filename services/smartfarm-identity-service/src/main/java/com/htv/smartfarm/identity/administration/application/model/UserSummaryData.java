package com.htv.smartfarm.identity.administration.application.model;

import java.time.Instant;
import java.util.List;

import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;

public record UserSummaryData(
        String subjectId,
        String membershipId,
        String tenantId,
        String email,
        String displayName,
        AccountStatusData accountStatus,
        MembershipStatus membershipStatus,
        List<String> roles,
        Instant createdAt,
        Instant updatedAt
) {

    public UserSummaryData {
        roles = roles == null
                ? List.of()
                : List.copyOf(roles);
    }
}