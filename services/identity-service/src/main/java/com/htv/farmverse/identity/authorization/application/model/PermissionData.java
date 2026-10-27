package com.htv.farmverse.identity.authorization.application.model;

public record PermissionData(
        String code,
        String resourceType,
        String action,
        String description
) {
}
