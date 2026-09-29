package com.htv.smartfarm.identity.account.application.command;

import java.util.Set;

public record UpdateProfileCommand(
        String subjectId,
        String displayName,
        String phoneNumber,
        String locale,
        String timeZone,
        Set<String> updateFields,
        long expectedVersion
) {

    public UpdateProfileCommand {
        updateFields = Set.copyOf(updateFields);
    }
}