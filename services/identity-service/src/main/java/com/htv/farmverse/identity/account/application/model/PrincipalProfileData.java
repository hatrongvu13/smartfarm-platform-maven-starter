package com.htv.farmverse.identity.account.application.model;

public record PrincipalProfileData(
        String displayName,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String avatarUrl,
        String locale,
        String timeZone,
        boolean emailVerified,
        boolean phoneVerified
) {
}
