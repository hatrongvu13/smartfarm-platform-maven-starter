# Order service operational SLO

## Scope

This SLO covers the public Gateway Order surface and the Order service asynchronous processing path.

## Objectives

- Gateway metrics target availability: at least 99.9% per calendar month.
- Order service metrics target availability: at least 99.9% per calendar month.
- Outbox pending age: below 5 minutes for 99% of evaluated 5-minute windows.
- Saga active age: below 15 minutes for 99% of evaluated 5-minute windows.
- Projection unresolved gap age: below 15 minutes for 99% of evaluated 5-minute windows.
- Dead outbox events: zero. Every occurrence requires operator review.

## Measurement

Use `up`, `smartfarm_order_outbox_oldest_pending_seconds`,
`smartfarm_order_saga_oldest_active_seconds`, and
`smartfarm_order_projection_gaps_oldest_seconds`. Do not aggregate by tenant or business identifiers.

## Error-budget policy

- Critical availability alerts consume error budget and require immediate incident ownership.
- Warning backlog alerts require investigation during the operating support window.
- Manual-review records are durable work items. They do not make readiness fail, but must be resolved within the owning team's response target.
