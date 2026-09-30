package com.htv.smartfarm.identity.administration.application.command;

public record AssignRoleCommand(
        String tenantId,
        String subjectId,
        String roleCode,
        String actorId
) {
}