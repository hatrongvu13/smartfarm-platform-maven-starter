package com.htv.smartfarm.order.readmodel.recovery;

import java.time.Clock;
import com.htv.smartfarm.order.readmodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderProjectionArchiveReplayService {
    private final OrderEventArchiveRepository archive;
    private final OrderProjectionInboxRepository inbox;
    private final OrderProjectionDrainService drain;
    private final Clock clock;
    public OrderProjectionArchiveReplayService(OrderEventArchiveRepository archive,
            OrderProjectionInboxRepository inbox, OrderProjectionDrainService drain, Clock clock) {
        this.archive = archive; this.inbox = inbox; this.drain = drain; this.clock = clock;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReplayResult replay(String tenantId, String aggregateId, long version) {
        var archived = archive.findByTenantIdAndAggregateIdAndAggregateVersion(
                tenantId, aggregateId, version).orElse(null);
        if (archived == null) return ReplayResult.missing(version);
        if (!inbox.existsById(archived.getEventId())) {
            var event = OrderEventArchiveMapper.event(archived);
            inbox.saveAndFlush(new OrderProjectionInboxEntity(archived.getEventId(), tenantId,
                    aggregateId, version, event.toByteArray(), clock.instant()));
        }
        OrderProjectionDrainResult result = drain.drain(tenantId, aggregateId);
        return new ReplayResult(true, archived.getEventId(), version,
                result.currentVersion(), result.appliedEvents(), result.gapRemaining());
    }
    public record ReplayResult(boolean archiveAvailable, String eventId, long replayedVersion,
            long projectionVersion, int appliedEvents, boolean gapRemaining) {
        static ReplayResult missing(long version) {
            return new ReplayResult(false, null, version, 0, 0, true);
        }
    }
}
