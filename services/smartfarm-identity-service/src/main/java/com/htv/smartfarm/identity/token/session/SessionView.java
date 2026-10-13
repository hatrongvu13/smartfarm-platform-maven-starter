package com.htv.smartfarm.identity.token.session;

import java.time.Instant;
public record SessionView(String sessionId,String subjectId,String tenantId,String audience,
        Instant createdAt,Instant lastUsedAt,Instant expiresAt,Instant revokedAt,String revokeReason) { }
