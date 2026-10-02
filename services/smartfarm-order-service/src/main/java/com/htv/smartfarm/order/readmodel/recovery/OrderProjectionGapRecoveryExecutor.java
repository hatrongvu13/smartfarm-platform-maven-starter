package com.htv.smartfarm.order.readmodel.recovery;

import org.springframework.stereotype.Service;

/**
 * Phase 3A.9 skeleton boundary. The next patch will reconstruct the archived DomainEvent,
 * insert it into ord_projection_inbox with the original eventId/version, invoke the shared
 * projection drain service, and return the resulting projection version.
 */
@Service
public class OrderProjectionGapRecoveryExecutor {
    private final OrderEventArchiveRepository archive;
    public OrderProjectionGapRecoveryExecutor(OrderEventArchiveRepository archive) {
        this.archive = archive;
    }
    public Result recover(OrderProjectionGapClaim claim) {
        var event = archive.findByTenantIdAndAggregateIdAndAggregateVersion(
                claim.tenantId(), claim.aggregateId(), claim.missingFromVersion()).orElse(null);
        if (event == null) return Result.missingArchive();
        return Result.archiveAvailable(event.getEventId(), event.getAggregateVersion());
    }
    public record Result(boolean archiveAvailable, String eventId, long aggregateVersion,
            boolean resolved, long projectionVersion) {
        public static Result missingArchive() { return new Result(false, null, 0, false, 0); }
        public static Result archiveAvailable(String eventId, long version) {
            return new Result(true, eventId, version, false, 0);
        }
    }
}
