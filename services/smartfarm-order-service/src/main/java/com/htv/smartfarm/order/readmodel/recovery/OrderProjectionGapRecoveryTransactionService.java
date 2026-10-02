package com.htv.smartfarm.order.readmodel.recovery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderProjectionGapRecoveryTransactionService {
    private final OrderProjectionGapRepository gaps;
    private final OrderProjectionRecoveryAuditRepository audit;
    private final OrderProjectionGapRecoveryProperties properties;
    private final Clock clock;
    public OrderProjectionGapRecoveryTransactionService(OrderProjectionGapRepository gaps,
            OrderProjectionRecoveryAuditRepository audit,
            OrderProjectionGapRecoveryProperties properties, Clock clock) {
        this.gaps = gaps; this.audit = audit; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public void openOrExtend(String tenantId, String aggregateId,
            long currentVersion, long incomingVersion) {
        if (incomingVersion <= currentVersion + 1) return;
        Instant now = clock.instant();
        OrderProjectionGapEntity gap = gaps.findAggregateForUpdate(tenantId, aggregateId).orElse(null);
        if (gap == null) {
            gaps.save(new OrderProjectionGapEntity(UUID.randomUUID().toString(), tenantId, aggregateId,
                    currentVersion, currentVersion + 1, incomingVersion - 1, incomingVersion, now));
        } else {
            gap.extend(currentVersion, incomingVersion, now);
        }
    }

    @Transactional
    public List<OrderProjectionGapClaim> claimBatch() {
        Instant now = clock.instant();
        var values = gaps.lockReady(List.of(OrderProjectionGapStatus.OPEN,
                OrderProjectionGapStatus.RETRY_WAIT), now, PageRequest.of(0, properties.batchSize()));
        values.forEach(value -> value.claim(properties.instanceId(), now));
        return values.stream().map(value -> new OrderProjectionGapClaim(
                value.getId(), value.getTenantId(), value.getAggregateId(), value.getCurrentVersion(),
                value.getMissingFromVersion(), value.getMissingToVersion(),
                value.getHighestBufferedVersion(), value.getAttemptCount())).toList();
    }

    @Transactional
    public void retry(OrderProjectionGapClaim claim, String code, String message) {
        Instant now = clock.instant();
        OrderProjectionGapEntity gap = gaps.findById(claim.gapId())
                .orElseThrow(() -> new IllegalArgumentException("projection gap not found"));
        int nextAttempt = gap.getAttemptCount() + 1;
        if (nextAttempt >= properties.maximumAttempts()
                || gap.getFirstDetectedAt().plus(properties.manualReviewAfter()).isBefore(now)) {
            String previous = gap.getStatus().name();
            gap.manualReview(code, message, now);
            audit(gap, null, null, OrderProjectionRecoveryAction.GAP_MANUAL_REVIEW,
                    previous, gap.getStatus().name(), message, now);
            return;
        }
        gap.retry(now.plus(retryDelay(nextAttempt)), code, message, now);
    }

    @Transactional
    public void resolved(OrderProjectionGapClaim claim, long projectionVersion) {
        Instant now = clock.instant();
        OrderProjectionGapEntity gap = gaps.findById(claim.gapId())
                .orElseThrow(() -> new IllegalArgumentException("projection gap not found"));
        String previous = gap.getStatus().name();
        gap.resolve(projectionVersion, now);
        audit(gap, projectionVersion, null, OrderProjectionRecoveryAction.GAP_RESOLVED,
                previous, gap.getStatus().name(), "Projection gap resolved", now);
    }

    @Transactional
    public int recoverStaleClaims() {
        Instant now = clock.instant();
        var values = gaps.lockStale(OrderProjectionGapStatus.RECOVERING,
                now.minus(properties.claimTimeout()), PageRequest.of(0, properties.batchSize()));
        for (OrderProjectionGapEntity gap : values) {
            String previous = gap.getStatus().name();
            gap.recoverStale(now);
            audit(gap, null, null, OrderProjectionRecoveryAction.STALE_CLAIM_RECOVERED,
                    previous, gap.getStatus().name(), "Recovered stale projection gap claim", now);
        }
        return values.size();
    }

    private void audit(OrderProjectionGapEntity gap, Long version, String eventId,
            OrderProjectionRecoveryAction action, String previous, String next,
            String reason, Instant now) {
        audit.save(new OrderProjectionRecoveryAuditEntity(UUID.randomUUID().toString(),
                gap.getTenantId(), gap.getAggregateId(), gap.getId(), version, eventId,
                properties.instanceId(), action, previous, next, reason, now));
    }

    private Duration retryDelay(int attempt) {
        long initial = properties.initialRetry().toMillis();
        long maximum = properties.maximumRetry().toMillis();
        long calculated = initial * (1L << Math.min(16, Math.max(0, attempt - 1)));
        return Duration.ofMillis(Math.min(maximum, calculated));
    }
}
