package com.htv.smartfarm.order.outbox;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.events.v1.DomainEvent;
import com.htv.smartfarm.proto.events.v1.EventMetadata;
import com.htv.smartfarm.proto.events.v1.OrderChanged;
import com.htv.smartfarm.proto.events.v1.OrderLifecycleEvent;
import com.htv.smartfarm.proto.order.v1.FarmOrder;
import com.htv.smartfarm.proto.order.v1.OrderStatus;
import java.util.regex.Pattern;

public final class OrderEventMapper {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private OrderEventMapper() { }

    public static String topic(OrderOutboxEvent event) {
        validateSegment(event.tenantId());
        validateSegment(event.farmId());
        if ("order-changed.v1".equals(event.eventType())) {
            return "smartfarm/" + event.tenantId() + "/" + event.farmId()
                    + "/domain/order-changed/v1";
        }
        var type = OrderBusinessEventType.fromEventType(event.eventType());
        return "smartfarm/" + event.tenantId() + "/" + event.farmId()
                + "/domain/order/" + type.topicName() + "/v1";
    }

    public static DomainEvent event(OrderOutboxEvent event) {
        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(event.eventId())
                .setTenantId(event.tenantId())
                .setFarmId(event.farmId())
                .setAggregateId(event.aggregateId())
                .setProducer("smartfarm-order-service")
                .setCorrelationId(empty(event.correlationId()))
                .setAggregateVersion(event.aggregateVersion())
                .setOccurredAt(timestamp(event.createdAt()))
                .build();
        FarmOrder order = order(event);
        DomainEvent.Builder result = DomainEvent.newBuilder().setMetadata(metadata);
        if ("order-changed.v1".equals(event.eventType())) {
            return result.setOrderChanged(OrderChanged.newBuilder().setOrder(order)).build();
        }
        OrderBusinessEventType.fromEventType(event.eventType());
        return result.setOrderLifecycleEvent(OrderLifecycleEvent.newBuilder()
                .setEventType(event.eventType())
                .setSagaId(empty(event.sagaId()))
                .setStepKey(empty(event.stepKey()))
                .setActorId(empty(event.actorId()))
                .setPreviousStatus(empty(event.previousStatus()))
                .setNewStatus(empty(event.newStatus()))
                .setReasonCode(empty(event.reasonCode()))
                .setReason(empty(event.reason()))
                .setOrder(order))
                .build();
    }

    private static FarmOrder order(OrderOutboxEvent event) {
        var builder = FarmOrder.newBuilder()
                .setOrderId(event.aggregateId())
                .setFarmId(event.farmId())
                .setStatus(status(event.status()))
                .setTotal(Money.newBuilder().setCurrencyCode(empty(event.currencyCode()))
                        .setMinorUnits(event.totalMinor()));
        if (event.batchId() != null) builder.setBatchId(event.batchId());
        if (event.failureReason() != null) builder.setFailureReason(event.failureReason());
        return builder.build();
    }

    private static Timestamp timestamp(long millis) {
        return Timestamp.newBuilder().setSeconds(Math.floorDiv(millis, 1000))
                .setNanos((int) Math.floorMod(millis, 1000) * 1_000_000).build();
    }

    private static OrderStatus status(String value) {
        if (value == null) return OrderStatus.ORDER_STATUS_UNSPECIFIED;
        try { return OrderStatus.valueOf(value); }
        catch (IllegalArgumentException ignored) { return OrderStatus.ORDER_STATUS_UNSPECIFIED; }
    }

    private static String empty(String value) { return value == null ? "" : value; }
    private static void validateSegment(String value) {
        if (value == null || !SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid MQTT topic segment");
        }
    }
}
