package com.htv.farmverse.identity.mfa.application.model;

import java.util.List;

public record TotpEnrollmentConfirmation(
        String authenticatorId,
        List<String> recoveryCodes
) {
    public TotpEnrollmentConfirmation {
        recoveryCodes = List.copyOf(recoveryCodes);
    }
}
