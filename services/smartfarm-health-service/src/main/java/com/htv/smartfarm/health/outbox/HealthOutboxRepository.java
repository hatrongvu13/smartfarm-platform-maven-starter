package com.htv.smartfarm.health.outbox;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Health outbox access facade. Backed by Spring Data JPA; the public
 * {@code Pending} record and {@code pending(int)} / {@code markPublished(String)}
 * API are unchanged from the former JdbcTemplate implementation so
 * {@link HealthOutboxRelay} is unaffected.
 */
@Repository
public class HealthOutboxRepository {

    private final HealthOutboxJpaRepository outbox;

    public HealthOutboxRepository(HealthOutboxJpaRepository outbox) {
        this.outbox = outbox;
    }

    public record Pending(String eventId, String tenantId, String farmId,
                          String eventType, byte[] payload) {
    }

    public List<Pending> pending(int limit) {
        return outbox.findByStatusOrderByEventId("NEW", PageRequest.of(0, limit)).stream()
                .map(e -> new Pending(e.getEventId(), e.getTenantId(), e.getFarmId(),
                        e.getEventType(), e.getPayload()))
                .toList();
    }

    @Transactional
    public int markPublished(String eventId) {
        return outbox.findById(eventId)
                .filter(e -> "NEW".equals(e.getStatus()))
                .map(e -> {
                    e.markPublished();
                    return 1;
                })
                .orElse(0);
    }
}
