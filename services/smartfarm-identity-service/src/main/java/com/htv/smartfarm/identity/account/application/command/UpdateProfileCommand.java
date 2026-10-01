package com.htv.smartfarm.identity.account.application.command;

import java.util.Set;

public record UpdateProfileCommand(
        String subjectId,
        String displayName,
        String firstName,
        String lastName,
        String phoneNumber,
        String avatarUrl,
        String locale,
        String timeZone,
        Set<String> updateFields,
        long expectedVersion
) {
    public UpdateProfileCommand {
        updateFields = updateFields == null ? Set.of() : Set.copyOf(updateFields);
    }
}
