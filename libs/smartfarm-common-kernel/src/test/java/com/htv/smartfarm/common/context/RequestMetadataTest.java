package com.htv.smartfarm.common.context;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RequestMetadataTest {
    @Test
    void normalizesValues() {
        var m = new RequestMetadata(" tenant ", " actor ", " corr ", " trace ", " idem ");
        assertEquals("tenant", m.tenantId());
        assertTrue(m.hasActor());
        assertEquals("corr", m.correlationId());
    }

    @Test
    void rejectsLongCorrelationId() {
        assertThrows(IllegalArgumentException.class,
                () -> new RequestMetadata(null, null, "a".repeat(129), null, null));
    }
}
