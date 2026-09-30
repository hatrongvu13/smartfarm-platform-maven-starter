package com.htv.smartfarm.identity.administration.application.command;

public record RevokeRoleCommand(
        String tenantId,
        String subjectId,
        String roleCode,
        String actorId
) {
}