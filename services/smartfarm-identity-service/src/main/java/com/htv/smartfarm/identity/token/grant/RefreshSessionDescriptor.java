package com.htv.smartfarm.identity.token.grant;

public record RefreshSessionDescriptor(
        String sessionId,
        String familyId,
        String subjectId,
        String tenantId,
        String audience
) { }
