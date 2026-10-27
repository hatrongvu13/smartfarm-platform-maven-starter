package com.htv.farmverse.identity.token.grant;

public record RefreshSessionDescriptor(
        String sessionId,
        String familyId,
        String subjectId,
        String tenantId,
        String audience
) { }
