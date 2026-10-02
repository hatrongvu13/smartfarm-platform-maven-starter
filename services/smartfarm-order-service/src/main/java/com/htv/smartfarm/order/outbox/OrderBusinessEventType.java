package com.htv.smartfarm.order.outbox;

public enum OrderBusinessEventType {
    CREATED("order.created.v1", "created"),
    STOCK_RESERVATION_REQUESTED("order.stock-reservation-requested.v1", "stock-reservation-requested"),
    STOCK_RESERVED("order.stock-reserved.v1", "stock-reserved"),
    STOCK_RESERVATION_FAILED("order.stock-reservation-failed.v1", "stock-reservation-failed"),
    FINANCE_POST_REQUESTED("order.finance-post-requested.v1", "finance-post-requested"),
    FINANCE_POSTED("order.finance-posted.v1", "finance-posted"),
    FINANCE_POST_FAILED("order.finance-post-failed.v1", "finance-post-failed"),
    COMPLETED("order.completed.v1", "completed"),
    CANCEL_REQUESTED("order.cancel-requested.v1", "cancel-requested"),
    CANCELLED("order.cancelled.v1", "cancelled"),
    COMPENSATION_STARTED("order.compensation-started.v1", "compensation-started"),
    COMPENSATION_COMPLETED("order.compensation-completed.v1", "compensation-completed"),
    COMPENSATION_FAILED("order.compensation-failed.v1", "compensation-failed"),
    MANUAL_REVIEW_REQUIRED("order.manual-review-required.v1", "manual-review-required"),
    MANUAL_REVIEW_RESOLVED("order.manual-review-resolved.v1", "manual-review-resolved");

    private final String eventType;
    private final String topicName;

    OrderBusinessEventType(String eventType, String topicName) {
        this.eventType = eventType;
        this.topicName = topicName;
    }

    public String eventType() { return eventType; }
    public String topicName() { return topicName; }

    public static OrderBusinessEventType fromEventType(String value) {
        for (OrderBusinessEventType type : values()) {
            if (type.eventType.equals(value)) return type;
        }
        throw new IllegalArgumentException("unsupported Order business event type: " + value);
    }
}
