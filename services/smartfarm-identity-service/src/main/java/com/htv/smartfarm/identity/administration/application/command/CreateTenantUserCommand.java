package com.htv.smartfarm.identity.administration.application.command;

import java.util.List;

public record CreateTenantUserCommand(
        String tenantId,
        String actorId,
        String email,
        String initialPassword,
        String displayName,
        String phoneNumber,
        String locale,
        String timeZone,
        List<String> initialRoleCodes,
        boolean activateImmediately
) {

    public CreateTenantUserCommand {
        initialRoleCodes = initialRoleCodes == null
                ? List.of()
                : List.copyOf(initialRoleCodes);
    }
}