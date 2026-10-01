package com.htv.smartfarm.identity.mfa.application.model;

import java.util.List;

public record SecurityProfile(
        String userId,
        boolean totpRequired,
        boolean totpEnabled,
        List<AuthenticatorSummary> authenticators
) {
    public SecurityProfile {
        authenticators = List.copyOf(authenticators);
    }

    public record AuthenticatorSummary(
            String id,
            String type,
            String status,
            String displayName
    ) {
    }
}
