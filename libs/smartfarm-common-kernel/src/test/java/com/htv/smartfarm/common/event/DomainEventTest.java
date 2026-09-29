package com.htv.smartfarm.common.event;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;

import org.junit.jupiter.api.Test;

class DomainEventTest {
    @Test
    void createsEventUsingFixedClock() {
        var now = Instant.parse("2026-09-30T00:00:00Z");
        var metadata = new EventMetadata("corr", "trace", "tenant", "actor", "identity");
        var event = DomainEvent.create("identity.user.created", "user-1", metadata, 1,
                "payload", Clock.fixed(now, ZoneOffset.UTC));
        assertEquals(now, event.occurredAt());
        assertNotNull(event.eventId());
    }

    @Test
    void rejectsInvalidSchemaVersion() {
        var metadata = new EventMetadata(null, null, null, null, "identity");
        assertThrows(IllegalArgumentException.class,
                () -> DomainEvent.create("event", "id", metadata, 0, "payload",
                        Clock.systemUTC()));
    }
}
