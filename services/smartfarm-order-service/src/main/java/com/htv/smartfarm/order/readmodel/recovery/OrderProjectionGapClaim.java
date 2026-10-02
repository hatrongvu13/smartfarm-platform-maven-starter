package com.htv.smartfarm.order.readmodel.recovery;

public record OrderProjectionGapClaim(
        String gapId,
        String tenantId,
        String aggregateId,
        long currentVersion,
        long missingFromVersion,
        long missingToVersion,
        long highestBufferedVersion,
        int attemptCount
) { }
