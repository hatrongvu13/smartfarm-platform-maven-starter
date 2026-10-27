package com.htv.farmverse.identity.authorization.application.model;

public record PermissionTarget(
        String resourceType,
        String resourceId,
        String action
) {
}