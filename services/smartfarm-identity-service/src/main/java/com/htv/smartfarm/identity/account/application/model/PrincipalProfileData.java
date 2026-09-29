package com.htv.smartfarm.identity.account.application.model;

public record PrincipalProfileData(
        String displayName,
        String email,
        String phoneNumber,
        String locale,
        String timeZone
) {
}
