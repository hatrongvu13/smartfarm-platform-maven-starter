package com.htv.smartfarm.identity.authorization.application.model;

public record PermissionTarget(
        String resourceType,
        String resourceId,
        String action
) {
}