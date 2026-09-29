package com.htv.smartfarm.common.outbox;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OutboxStatusTest {
    @Test
    void identifiesTerminalStates() {
        assertTrue(OutboxStatus.PUBLISHED.terminal());
        assertTrue(OutboxStatus.DEAD.terminal());
        assertFalse(OutboxStatus.NEW.terminal());
    }

    @Test
    void identifiesRetryableStates() {
        assertTrue(OutboxStatus.NEW.retryable());
        assertTrue(OutboxStatus.FAILED.retryable());
        assertFalse(OutboxStatus.DEAD.retryable());
    }
}
