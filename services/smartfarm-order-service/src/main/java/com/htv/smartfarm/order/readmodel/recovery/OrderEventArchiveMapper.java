package com.htv.smartfarm.order.readmodel.recovery;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.events.v1.*;
import com.htv.smartfarm.proto.order.v1.*;

public final class OrderEventArchiveMapper {
    private OrderEventArchiveMapper() { }
    public static DomainEvent event(OrderEventArchiveEntity value) {
        EventMetadata metadata = EventMetadata.newBuilder()
                .setEventId(value.getEventId()).setTenantId(value.getTenantId())
                .setFarmId(value.getFarmId()).setAggregateId(value.getAggregateId())
                .setAggregateVersion(value.getAggregateVersion())
                .setCorrelationId(value.getCorrelationId() == null ? "" : value.getCorrelationId())
                .setProducer("smartfarm-order-service")
                .setOccurredAt(Timestamp.newBuilder()
                        .setSeconds(Math.floorDiv(value.getCreatedAt(), 1000))
                        .setNanos((int) Math.floorMod(value.getCreatedAt(), 1000) * 1_000_000))
                .build();
        FarmOrder.Builder order = FarmOrder.newBuilder()
                .setOrderId(value.getAggregateId()).setFarmId(value.getFarmId())
                .setStatus(status(value.getOrderStatus()))
                .setTotal(Money.newBuilder().setCurrencyCode(value.getCurrencyCode())
                        .setMinorUnits(value.getTotalMinor()));
        if (value.getBatchId() != null) order.setBatchId(value.getBatchId());
        if (value.getFailureReason() != null) order.setFailureReason(value.getFailureReason());
        return DomainEvent.newBuilder().setMetadata(metadata)
                .setOrderChanged(OrderChanged.newBuilder().setOrder(order)).build();
    }
    private static OrderStatus status(String value) {
        try { return OrderStatus.valueOf(value); }
        catch (IllegalArgumentException invalid) { return OrderStatus.ORDER_STATUS_UNSPECIFIED; }
    }
}
