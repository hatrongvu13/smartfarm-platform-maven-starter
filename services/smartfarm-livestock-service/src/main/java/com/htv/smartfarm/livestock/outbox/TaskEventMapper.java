package com.htv.smartfarm.livestock.outbox;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.proto.events.v1.*;
import com.htv.smartfarm.proto.livestock.v1.*;

import java.util.regex.Pattern;

/**
 * Maps a committed task snapshot to the versioned protobuf integration event. Supports the
 * full task lifecycle (created / assigned / accepted / completed / cancelled) plus the two
 * deadline-breach events emitted by the monitor. Topic segment is derived from the event type
 * so consumers can subscribe per concern.
 */
public final class TaskEventMapper {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private TaskEventMapper() {
    }

    /** Maps an outbox event type to its MQTT topic leaf, e.g. task-assigned.v1 -> task-assigned/v1. */
    private static String leaf(String eventType) {
        return switch (eventType) {
            case "task-created.v1" -> "task-changed/v1";
            case "task-assigned.v1" -> "task-assigned/v1";
            case "task-accepted.v1" -> "task-accepted/v1";
            case "task-completed.v1" -> "task-completed/v1";
            case "task-cancelled.v1" -> "task-cancelled/v1";
            case "task-accept-overdue.v1" -> "task-accept-overdue/v1";
            case "task-report-overdue.v1" -> "task-report-overdue/v1";
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + eventType);
        };
    }

    public static String topic(OutboxEvent e) {
        if (!SEGMENT.matcher(e.tenantId()).matches() || !SEGMENT.matcher(e.farmId()).matches())
            throw new IllegalArgumentException("Invalid tenant/farm MQTT topic segment");
        return "smartfarm/" + e.tenantId() + "/" + e.farmId() + "/domain/" + leaf(e.eventType());
    }

    private static TaskStatus status(String name) {
        if (name == null) return TaskStatus.TASK_STATUS_UNSPECIFIED;
        try {
            return TaskStatus.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return TaskStatus.TASK_STATUS_UNSPECIFIED;
        }
    }

    private static EventMetadata metadata(OutboxEvent e) {
        return EventMetadata.newBuilder().setEventId(e.eventId()).setTenantId(e.tenantId())
                .setFarmId(e.farmId()).setAggregateId(e.aggregateId()).setProducer("smartfarm-livestock-service")
                .setCorrelationId(e.correlationId() == null ? "" : e.correlationId())
                .setOccurredAt(Timestamp.newBuilder().setSeconds(Math.floorDiv(e.createdAt(), 1000))
                        .setNanos((int) Math.floorMod(e.createdAt(), 1000) * 1_000_000).build()).build();
    }

    public static DomainEvent event(OutboxEvent e) {
        var task = LivestockTask.newBuilder().setTaskId(e.aggregateId()).setFarmId(e.farmId())
                .setTitle(e.title() == null ? "" : e.title())
                .setAssigneeId(e.assigneeId() == null ? "" : e.assigneeId())
                .setStatus(status(e.taskStatus())).build();
        var meta = metadata(e);
        return switch (e.eventType()) {
            case "task-accept-overdue.v1" -> DomainEvent.newBuilder().setMetadata(meta)
                    .setTaskDeadlineBreached(TaskDeadlineBreached.newBuilder().setTask(task)
                            .setKind(TaskDeadlineKind.TASK_DEADLINE_KIND_ACCEPT_OVERDUE)).build();
            case "task-report-overdue.v1" -> DomainEvent.newBuilder().setMetadata(meta)
                    .setTaskDeadlineBreached(TaskDeadlineBreached.newBuilder().setTask(task)
                            .setKind(TaskDeadlineKind.TASK_DEADLINE_KIND_REPORT_OVERDUE)).build();
            case "task-created.v1", "task-assigned.v1", "task-accepted.v1",
                 "task-completed.v1", "task-cancelled.v1" ->
                    DomainEvent.newBuilder().setMetadata(meta).setTaskChanged(TaskChanged.newBuilder().setTask(task)).build();
            default -> throw new IllegalArgumentException("Unsupported outbox event type: " + e.eventType());
        };
    }
}
