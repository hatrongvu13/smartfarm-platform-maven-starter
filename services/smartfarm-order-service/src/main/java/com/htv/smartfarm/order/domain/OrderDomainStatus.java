package com.htv.smartfarm.order.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderDomainStatus {
    DRAFT,
    CREATED,
    STOCK_RESERVED,
    TASK_SCHEDULED,
    FINANCE_POSTED,
    COMPLETED,
    FAILED,
    CANCELLED,
    MANUAL_REVIEW;

    public String protoName() {
        return "ORDER_STATUS_" + name();
    }

    public static OrderDomainStatus fromStored(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("order status must not be blank");
        }
        String normalized = value.startsWith("ORDER_STATUS_")
                ? value.substring("ORDER_STATUS_".length())
                : value;
        return valueOf(normalized);
    }

    public Set<OrderDomainStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> EnumSet.of(CREATED, CANCELLED);
            case CREATED -> EnumSet.of(STOCK_RESERVED, FAILED, CANCELLED, MANUAL_REVIEW);
            case STOCK_RESERVED -> EnumSet.of(TASK_SCHEDULED, FINANCE_POSTED, FAILED, CANCELLED, MANUAL_REVIEW);
            case TASK_SCHEDULED -> EnumSet.of(FINANCE_POSTED, FAILED, CANCELLED, MANUAL_REVIEW);
            case FINANCE_POSTED -> EnumSet.of(COMPLETED, FAILED, CANCELLED, MANUAL_REVIEW);
            case FAILED -> EnumSet.of(CANCELLED, MANUAL_REVIEW);
            case MANUAL_REVIEW -> EnumSet.of(FAILED, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(OrderDomainStatus.class);
        };
    }

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean editable() {
        return this == DRAFT;
    }
}
