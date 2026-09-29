package com.htv.smartfarm.identity.account.application.command;

public record CreateAccountCommand(
        String email,
        String rawPassword,
        String displayName,
        String phoneNumber,
        String locale,
        String timeZone
) {
}
