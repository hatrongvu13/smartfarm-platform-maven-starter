package com.htv.smartfarm.order.outbox.administration;

import java.time.Clock;
import java.util.List;
import com.htv.smartfarm.order.outbox.OrderOutboxEntity;
import com.htv.smartfarm.order.outbox.OrderOutboxJpaRepository;
import com.htv.smartfarm.order.outbox.OrderOutboxStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderOutboxAdministrationService {
    private final OrderOutboxJpaRepository outbox;
    private final Clock clock;

    public OrderOutboxAdministrationService(OrderOutboxJpaRepository outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OrderOutboxInspection inspect(String tenantId, String eventId) {
        return data(eventForTenant(tenantId, eventId));
    }

    @Transactional(readOnly = true)
    public List<OrderOutboxInspection> list(String tenantId, OrderOutboxStatus status,
            int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return outbox.findByTenantIdAndStatusOrderByCreatedAtAscEventIdAsc(
                required(tenantId, "tenantId"), status,
                PageRequest.of(safePage, safeSize)).stream().map(this::data).toList();
    }

    @Transactional
    public OrderOutboxInspection retry(String tenantId, String eventId) {
        OrderOutboxEntity event = eventForTenant(tenantId, eventId);
        event.retryManually(clock.instant());
        return data(event);
    }

    private OrderOutboxEntity eventForTenant(String tenantId, String eventId) {
        return outbox.findById(required(eventId, "eventId"))
                .filter(value -> value.getTenantId().equals(required(tenantId, "tenantId")))
                .orElseThrow(() -> new IllegalArgumentException("outbox event not found"));
    }

    private OrderOutboxInspection data(OrderOutboxEntity value) {
        return new OrderOutboxInspection(value.getEventId(), value.getTenantId(),
                value.getAggregateId(), value.getAggregateVersion(), value.getEventType(),
                value.getStatus().name(), value.getAttemptCount(), value.getNextAttemptAt(),
                value.getClaimedAt(), value.getLastErrorCode(), value.getCreatedAt());
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
