package com.htv.farmverse.identity.administration.application.command;

public record RevokeRoleCommand(
        String tenantId,
        String subjectId,
        String roleCode,
        String actorId
) {
}