package com.htv.smartfarm.identity.tenant.application.model;

import java.util.List;

import com.htv.smartfarm.identity.tenant.domain.MembershipStatus;

public record TenantMembershipData(
        String membershipId,
        String tenantId,
        MembershipStatus status,
        List<String> roles,
        List<String> farmIds
) {

    public TenantMembershipData {
        roles = List.copyOf(roles);
        farmIds = List.copyOf(farmIds);
    }
}