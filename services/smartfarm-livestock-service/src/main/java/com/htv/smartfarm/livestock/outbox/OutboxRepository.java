package com.htv.smartfarm.livestock.outbox;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox access facade. Backed by Spring Data JPA; the public
 * {@code pending(int)} / {@code markPublished(String)} API is unchanged from the
 * former JdbcTemplate implementation so {@link OutboxRelay} is unaffected.
 */
@Repository
public class OutboxRepository {

    private final OutboxJpaRepository outbox;

    public OutboxRepository(OutboxJpaRepository outbox) {
        this.outbox = outbox;
    }

    public List<OutboxEvent> pending(int limit) {
        return outbox.findPending(PageRequest.of(0, limit));
    }

    /**
     * Flip NEW -> PUBLISHED. Returns 1 when a NEW row was transitioned, 0 otherwise
     * (already published / missing), matching the former conditional UPDATE.
     */
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
