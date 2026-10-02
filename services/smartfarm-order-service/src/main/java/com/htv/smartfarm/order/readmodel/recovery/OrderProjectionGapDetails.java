package com.htv.smartfarm.order.readmodel.recovery;

import java.time.Instant;
import java.util.List;

public record OrderProjectionGapDetails(
        String gapId,
        String tenantId,
        String aggregateId,
        long currentVersion,
        long missingFromVersion,
        long missingToVersion,
        long highestBufferedVersion,
        String status,
        int attemptCount,
        Instant firstDetectedAt,
        Instant lastCheckedAt,
        Instant nextRecoveryAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant resolvedAt,
        List<Audit> audit
) {
    public record Audit(
            String auditId,
            Long aggregateVersion,
            String eventId,
            String actorId,
            String action,
            String previousStatus,
            String newStatus,
            String reason,
            Instant occurredAt
    ) { }
}
