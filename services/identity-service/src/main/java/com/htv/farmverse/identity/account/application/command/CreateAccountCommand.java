package com.htv.farmverse.identity.account.application.command;

public record CreateAccountCommand(
        String email,
        String rawPassword,
        String displayName,
        String phoneNumber,
        String locale,
        String timeZone
) {
}
