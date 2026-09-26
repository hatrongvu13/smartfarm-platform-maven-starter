package com.htv.smartfarm.health.outbox;

import com.htv.smartfarm.proto.events.v1.DomainEvent;
import com.htv.smartfarm.proto.events.v1.EventMetadata;
import com.htv.smartfarm.proto.events.v1.ObservationRecorded;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HealthOutboxRelayTest {
    private HealthOutboxRepository.Pending pending() {
        byte[] bytes = DomainEvent.newBuilder()
                .setMetadata(EventMetadata.newBuilder().setEventId("evt-1")
                        .setTenantId("tenant-1").setFarmId("farm-1"))
                .setObservationRecorded(ObservationRecorded.newBuilder().build())
                .build().toByteArray();
        return new HealthOutboxRepository.Pending("evt-1", "tenant-1", "farm-1",
                "observation-recorded.v1", bytes);
    }

    @Test void confirmsBeforeMarkingPublished() throws Exception {
        var repo = mock(HealthOutboxRepository.class);
        var publisher = mock(HealthEventPublisher.class);
        when(repo.pending(50)).thenReturn(List.of(pending()));
        new HealthOutboxRelay(repo, publisher).publishPending();
        var order = inOrder(publisher, repo);
        order.verify(publisher).publish(eq("smartfarm/tenant-1/farm-1/domain/observation-recorded/v1"), any(byte[].class));
        order.verify(repo).markPublished("evt-1");
    }

    @Test void brokerFailureLeavesNewRow() throws Exception {
        var repo = mock(HealthOutboxRepository.class);
        var publisher = mock(HealthEventPublisher.class);
        when(repo.pending(50)).thenReturn(List.of(pending()));
        doThrow(new IllegalStateException("offline")).when(publisher).publish(anyString(), any(byte[].class));
        new HealthOutboxRelay(repo, publisher).publishPending();
        verify(repo, never()).markPublished(anyString());
    }

    @Test void rejectsTopicInjection() {
        var row = new HealthOutboxRepository.Pending("evt-1", "tenant/+", "farm-1",
                "observation-recorded.v1", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> HealthOutboxRelay.topic(row));
    }
}
