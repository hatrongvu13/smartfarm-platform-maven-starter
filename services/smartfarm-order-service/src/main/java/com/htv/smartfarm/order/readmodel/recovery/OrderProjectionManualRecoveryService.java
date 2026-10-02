package com.htv.smartfarm.order.readmodel.recovery;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import com.htv.smartfarm.order.readmodel.OrderViewJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderProjectionManualRecoveryService {
    private final OrderProjectionGapRepository gaps;
    private final OrderProjectionRecoveryAuditRepository audit;
    private final OrderProjectionArchiveReplayService replay;
    private final OrderEventArchiveRepository archive;
    private final OrderViewJpaRepository views;
    private final Clock clock;

    public OrderProjectionManualRecoveryService(OrderProjectionGapRepository gaps,
            OrderProjectionRecoveryAuditRepository audit,
            OrderProjectionArchiveReplayService replay,
            OrderEventArchiveRepository archive,
            OrderViewJpaRepository views,
            Clock clock) {
        this.gaps = gaps; this.audit = audit; this.replay = replay;
        this.archive = archive; this.views = views; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OrderProjectionGapDetails inspect(String tenantId, String gapId) {
        return details(gapForTenant(tenantId, gapId), true);
    }

    @Transactional(readOnly = true)
    public OrderProjectionGapPage list(String tenantId, String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        var pageable = PageRequest.of(safePage, safeSize);
        var values = status == null || status.isBlank()
                ? gaps.findByTenantId(tenantId, pageable)
                : gaps.findByTenantIdAndStatus(tenantId,
                        OrderProjectionGapStatus.valueOf(status.trim().toUpperCase()), pageable);
        return new OrderProjectionGapPage(
                values.getContent().stream().map(value -> details(value, false)).toList(),
                values.getNumber(), values.getSize(), values.getTotalElements(), values.getTotalPages());
    }

    @Transactional
    public OrderProjectionGapDetails retry(String tenantId, String gapId,
            String actorId, String reason) {
        Instant now = clock.instant();
        OrderProjectionGapEntity gap = gapForTenant(tenantId, gapId);
        String previous = gap.getStatus().name();
        gap.retryManually(now);
        saveAudit(gap, null, null, actorId, OrderProjectionRecoveryAction.MANUAL_RETRY,
                previous, gap.getStatus().name(), required(reason, "reason"), now);
        return details(gap, true);
    }

    @Transactional
    public OrderProjectionGapDetails replayExpected(String tenantId, String gapId,
            long requestedVersion, String actorId, String reason) {
        required(actorId, "actorId");
        required(reason, "reason");
        OrderProjectionGapEntity snapshot = gapForTenantReadOnly(tenantId, gapId);
        long projectionVersion = views.findByTenantIdAndOrderId(tenantId, snapshot.getAggregateId())
                .map(value -> value.getLastAggregateVersion()).orElse(0L);
        long expected = projectionVersion + 1;
        if (requestedVersion != expected || requestedVersion != snapshot.getMissingFromVersion()) {
            throw new IllegalStateException("only the next missing aggregate version can be replayed: expected=" + expected);
        }
        var archived = archive.findByTenantIdAndAggregateIdAndAggregateVersion(
                tenantId, snapshot.getAggregateId(), requestedVersion)
                .orElseThrow(() -> new IllegalStateException("archive event not found for expected version"));
        var result = replay.replay(tenantId, snapshot.getAggregateId(), requestedVersion);
        return recordReplayResult(tenantId, gapId, actorId, reason, archived, result);
    }

    private OrderProjectionGapDetails recordReplayResult(String tenantId, String gapId,
            String actorId, String reason, OrderEventArchiveEntity archived,
            OrderProjectionArchiveReplayService.ReplayResult result) {
        OrderProjectionGapEntity gap = gapForTenant(tenantId, gapId);
        String previous = gap.getStatus().name();
        if (!result.archiveAvailable()) {
            throw new IllegalStateException("archive event not found for expected version");
        }
        if (!result.gapRemaining() && result.projectionVersion() >= gap.getHighestBufferedVersion()) {
            gap.resolve(result.projectionVersion(), clock.instant());
        } else {
            gap.progress(result.projectionVersion(), result.projectionVersion() + 1, clock.instant());
        }
        saveAudit(gap, archived.getAggregateVersion(), archived.getEventId(), actorId,
                OrderProjectionRecoveryAction.MANUAL_REPLAY, previous, gap.getStatus().name(),
                required(reason, "reason"), clock.instant());
        return details(gap, true);
    }

    private OrderProjectionGapEntity gapForTenant(String tenantId, String gapId) {
        return gaps.findByTenantIdAndId(required(tenantId, "tenantId"), required(gapId, "gapId"))
                .orElseThrow(() -> new IllegalArgumentException("projection gap not found"));
    }
    private OrderProjectionGapEntity gapForTenantReadOnly(String tenantId, String gapId) {
        return gapForTenant(tenantId, gapId);
    }
    private OrderProjectionGapDetails details(OrderProjectionGapEntity value, boolean includeAudit) {
        var auditValues = includeAudit
                ? audit.findTop100ByTenantIdAndGapIdOrderByOccurredAtDesc(value.getTenantId(), value.getId())
                    .stream().map(entry -> new OrderProjectionGapDetails.Audit(
                            entry.getId(), entry.getAggregateVersion(), entry.getEventId(),
                            entry.getActorId(), entry.getAction().name(), entry.getPreviousStatus(),
                            entry.getNewStatus(), entry.getReason(), entry.getOccurredAt())).toList()
                : java.util.List.<OrderProjectionGapDetails.Audit>of();
        return new OrderProjectionGapDetails(value.getId(), value.getTenantId(), value.getAggregateId(),
                value.getCurrentVersion(), value.getMissingFromVersion(), value.getMissingToVersion(),
                value.getHighestBufferedVersion(), value.getStatus().name(), value.getAttemptCount(),
                value.getFirstDetectedAt(), value.getLastCheckedAt(), value.getNextRecoveryAt(),
                value.getLastErrorCode(), value.getLastErrorMessage(), value.getResolvedAt(), auditValues);
    }
    private void saveAudit(OrderProjectionGapEntity gap, Long version, String eventId,
            String actorId, OrderProjectionRecoveryAction action, String previous,
            String next, String reason, Instant now) {
        audit.save(new OrderProjectionRecoveryAuditEntity(UUID.randomUUID().toString(),
                gap.getTenantId(), gap.getAggregateId(), gap.getId(), version, eventId,
                required(actorId, "actorId"), action, previous, next, reason, now));
    }
    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
