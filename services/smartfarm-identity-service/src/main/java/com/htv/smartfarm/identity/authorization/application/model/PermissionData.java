package com.htv.smartfarm.identity.authorization.application.model;

public record PermissionData(
        String code,
        String resourceType,
        String action,
        String description
) {
}
