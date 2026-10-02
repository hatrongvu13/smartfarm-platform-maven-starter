package com.htv.smartfarm.order.readmodel;

import java.time.Clock;
import com.htv.smartfarm.order.readmodel.recovery.OrderProjectionGapRecoveryTransactionService;
import com.htv.smartfarm.proto.events.v1.DomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderProjectionApplyTransaction {
    private final OrderProjectionInboxRepository inbox;
    private final OrderViewJpaRepository views;
    private final OrderProjectionDrainService drain;
    private final OrderProjectionGapRecoveryTransactionService gaps;
    private final Clock clock;
    public OrderProjectionApplyTransaction(OrderProjectionInboxRepository inbox,
            OrderViewJpaRepository views, OrderProjectionDrainService drain,
            OrderProjectionGapRecoveryTransactionService gaps, Clock clock) {
        this.inbox = inbox; this.views = views; this.drain = drain; this.gaps = gaps; this.clock = clock;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void receive(DomainEvent event, byte[] payload) {
        var metadata = event.getMetadata();
        if (inbox.existsById(metadata.getEventId())) return;
        long current = views.findByTenantIdAndOrderIdForUpdate(
                metadata.getTenantId(), metadata.getAggregateId())
                .map(OrderView::getLastAggregateVersion).orElse(0L);
        if (metadata.getAggregateVersion() <= current) {
            var stale = new OrderProjectionInboxEntity(metadata.getEventId(), metadata.getTenantId(),
                    metadata.getAggregateId(), metadata.getAggregateVersion(), payload, clock.instant());
            inbox.save(stale); stale.ignored(clock.instant()); return;
        }
        inbox.saveAndFlush(new OrderProjectionInboxEntity(metadata.getEventId(), metadata.getTenantId(),
                metadata.getAggregateId(), metadata.getAggregateVersion(), payload, clock.instant()));
        if (metadata.getAggregateVersion() > current + 1) {
            gaps.openOrExtend(metadata.getTenantId(), metadata.getAggregateId(),
                    current, metadata.getAggregateVersion());
        }
        drain.drain(metadata.getTenantId(), metadata.getAggregateId());
    }
}
