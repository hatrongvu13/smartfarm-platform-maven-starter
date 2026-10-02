package com.htv.smartfarm.order.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderDomainStatus {
    DRAFT,
    CREATED,
    STOCK_RESERVING,
    STOCK_RESERVED,
    TASK_SCHEDULED,
    FINANCE_POSTING,
    FINANCE_POSTED,
    COMPLETED,
    CANCELLING,
    COMPENSATING,
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
            case CREATED -> EnumSet.of(STOCK_RESERVING, CANCELLING, COMPENSATING, FAILED, CANCELLED, MANUAL_REVIEW);
            case STOCK_RESERVING -> EnumSet.of(STOCK_RESERVED, CANCELLING, COMPENSATING, FAILED, MANUAL_REVIEW);
            case STOCK_RESERVED -> EnumSet.of(TASK_SCHEDULED, FINANCE_POSTING, CANCELLING, COMPENSATING, FAILED, MANUAL_REVIEW);
            case TASK_SCHEDULED -> EnumSet.of(FINANCE_POSTING, CANCELLING, COMPENSATING, FAILED, MANUAL_REVIEW);
            case FINANCE_POSTING -> EnumSet.of(FINANCE_POSTED, CANCELLING, COMPENSATING, FAILED, MANUAL_REVIEW);
            case FINANCE_POSTED -> EnumSet.of(COMPLETED, CANCELLING, COMPENSATING, FAILED, MANUAL_REVIEW);
            case CANCELLING -> EnumSet.of(COMPENSATING, CANCELLED, MANUAL_REVIEW);
            case COMPENSATING -> EnumSet.of(FAILED, CANCELLED, MANUAL_REVIEW);
            case FAILED -> EnumSet.of(CANCELLING, COMPENSATING, CANCELLED, MANUAL_REVIEW);
            case MANUAL_REVIEW -> EnumSet.of(CANCELLING, COMPENSATING, FAILED, CANCELLED);
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
