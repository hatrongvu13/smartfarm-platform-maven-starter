package com.htv.smartfarm.common.event;

import static org.junit.jupiter.api.Assertions.*;

import com.htv.smartfarm.common.context.RequestMetadata;
import org.junit.jupiter.api.Test;

class EventMetadataTest {
    @Test
    void mapsRequestMetadata() {
        var request = new RequestMetadata("tenant", "actor", "corr", "trace", "idem");
        var event = EventMetadata.from(request, "identity");
        assertEquals("tenant", event.tenantId());
        assertEquals("identity", event.sourceService());
    }
}
