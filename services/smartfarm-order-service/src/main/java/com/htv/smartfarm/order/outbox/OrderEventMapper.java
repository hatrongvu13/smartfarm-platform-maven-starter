package com.htv.smartfarm.order.outbox;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.events.v1.DomainEvent;
import com.htv.smartfarm.proto.events.v1.EventMetadata;
import com.htv.smartfarm.proto.events.v1.OrderChanged;
import com.htv.smartfarm.proto.order.v1.FarmOrder;
import com.htv.smartfarm.proto.order.v1.OrderStatus;

import java.util.regex.Pattern;

/** Maps a committed order snapshot to the versioned {@code OrderChanged} integration event. */
public final class OrderEventMapper {
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private OrderEventMapper() {
    }

    public static String topic(OrderOutboxEvent e) {
        if (!SEGMENT.matcher(e.tenantId()).matches() || !SEGMENT.matcher(e.farmId()).matches())
            throw new IllegalArgumentException("Invalid tenant/farm MQTT topic segment");
        return "smartfarm/" + e.tenantId() + "/" + e.farmId() + "/domain/order-changed/v1";
    }

    private static OrderStatus status(String name) {
        if (name == null) return OrderStatus.ORDER_STATUS_UNSPECIFIED;
        try {
            return OrderStatus.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return OrderStatus.ORDER_STATUS_UNSPECIFIED;
        }
    }

    public static DomainEvent event(OrderOutboxEvent e) {
        if (!"order-changed.v1".equals(e.eventType()))
            throw new IllegalArgumentException("Unsupported outbox event type: " + e.eventType());
        var meta = EventMetadata.newBuilder().setEventId(e.eventId()).setTenantId(e.tenantId())
                .setFarmId(e.farmId()).setAggregateId(e.aggregateId()).setProducer("smartfarm-order-service")
                .setCorrelationId(e.correlationId() == null ? "" : e.correlationId())
                .setAggregateVersion(e.aggregateVersion())
                .setOccurredAt(Timestamp.newBuilder().setSeconds(Math.floorDiv(e.createdAt(), 1000))
                        .setNanos((int) Math.floorMod(e.createdAt(), 1000) * 1_000_000).build()).build();
        var order = FarmOrder.newBuilder()
                .setOrderId(e.aggregateId()).setFarmId(e.farmId())
                .setStatus(status(e.status()))
                .setTotal(Money.newBuilder().setCurrencyCode(e.currencyCode() == null ? "" : e.currencyCode())
                        .setMinorUnits(e.totalMinor()));
        if (e.batchId() != null) order.setBatchId(e.batchId());
        if (e.failureReason() != null) order.setFailureReason(e.failureReason());
        return DomainEvent.newBuilder().setMetadata(meta).setOrderChanged(OrderChanged.newBuilder().setOrder(order)).build();
    }
}
