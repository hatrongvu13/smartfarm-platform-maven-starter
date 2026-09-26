package com.htv.smartfarm.livestock.outbox;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.proto.events.v1.*;
import com.htv.smartfarm.proto.livestock.v1.*;

import java.util.regex.Pattern;

/**
 * Maps a committed task snapshot to the versioned protobuf integration event.
 */
public final class TaskEventMapper {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private TaskEventMapper() {
    }

    public static String topic(OutboxEvent e) {
        if (!SEGMENT.matcher(e.tenantId()).matches() || !SEGMENT.matcher(e.farmId()).matches())
            throw new IllegalArgumentException("Invalid tenant/farm MQTT topic segment");
        return "smartfarm/" + e.tenantId() + "/" + e.farmId() + "/domain/task-changed/v1";
    }

    public static DomainEvent event(OutboxEvent e) {
        if (!"task-created.v1".equals(e.eventType()))
            throw new IllegalArgumentException("Unsupported outbox event type");
        var metadata = EventMetadata.newBuilder().setEventId(e.eventId()).setTenantId(e.tenantId())
                .setFarmId(e.farmId()).setAggregateId(e.aggregateId()).setProducer("smartfarm-livestock-service")
                .setCorrelationId(e.correlationId() == null ? "" : e.correlationId())
                .setOccurredAt(Timestamp.newBuilder().setSeconds(Math.floorDiv(e.createdAt(), 1000))
                        .setNanos((int) Math.floorMod(e.createdAt(), 1000) * 1_000_000).build()).build();
        var task = LivestockTask.newBuilder().setTaskId(e.aggregateId()).setFarmId(e.farmId())
                .setTitle(e.title()).setAssigneeId(e.assigneeId()).setStatus(TaskStatus.TASK_STATUS_CREATED).build();
        return DomainEvent.newBuilder().setMetadata(metadata).setTaskChanged(TaskChanged.newBuilder().setTask(task)).build();
    }
}
