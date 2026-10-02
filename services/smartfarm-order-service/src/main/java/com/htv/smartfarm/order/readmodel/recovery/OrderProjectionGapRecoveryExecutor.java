package com.htv.smartfarm.order.readmodel.recovery;

import org.springframework.stereotype.Service;

@Service
public class OrderProjectionGapRecoveryExecutor {
    private final OrderProjectionArchiveReplayService replay;
    public OrderProjectionGapRecoveryExecutor(OrderProjectionArchiveReplayService replay) {
        this.replay = replay;
    }
    public Result recover(OrderProjectionGapClaim claim) {
        var result = replay.replay(claim.tenantId(), claim.aggregateId(), claim.missingFromVersion());
        if (!result.archiveAvailable()) return Result.missingArchive();
        boolean resolved = !result.gapRemaining()
                && result.projectionVersion() >= claim.highestBufferedVersion();
        return new Result(true, result.eventId(), result.replayedVersion(), resolved,
                result.projectionVersion(), result.appliedEvents());
    }
    public record Result(boolean archiveAvailable, String eventId, long aggregateVersion,
            boolean resolved, long projectionVersion, int appliedEvents) {
        static Result missingArchive() { return new Result(false, null, 0, false, 0, 0); }
    }
}
