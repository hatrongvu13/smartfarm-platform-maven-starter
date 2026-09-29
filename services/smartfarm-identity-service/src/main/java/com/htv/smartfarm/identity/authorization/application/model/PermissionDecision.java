package com.htv.smartfarm.identity.authorization.application.model;

public record PermissionDecision(
        PermissionTarget target,
        boolean allowed,
        String reasonCode
) {

    public static PermissionDecision allow(
            PermissionTarget target,
            String reasonCode
    ) {
        return new PermissionDecision(
                target,
                true,
                reasonCode
        );
    }

    public static PermissionDecision deny(
            PermissionTarget target,
            String reasonCode
    ) {
        return new PermissionDecision(
                target,
                false,
                reasonCode
        );
    }
}