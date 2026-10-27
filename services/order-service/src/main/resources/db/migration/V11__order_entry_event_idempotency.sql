CREATE UNIQUE INDEX IF NOT EXISTS uq_ord_outbox_order_created
    ON ord_outbox (tenant_id, aggregate_id, event_type)
    WHERE event_type = 'order.created.v1';

CREATE UNIQUE INDEX IF NOT EXISTS uq_ord_outbox_order_cancel_requested
    ON ord_outbox (tenant_id, aggregate_id, event_type)
    WHERE event_type = 'order.cancel-requested.v1';
