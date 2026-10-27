# Order and Gateway deployment

Replace image placeholders with immutable digests. Create `farmverse-order-config`,
`api-gateway-config`, `farmverse-order-secrets`, and `api-gateway-secrets`
outside Git. Never commit Secret values.

Required Order secrets include `ORDER_DB_PASSWORD`, `ORDER_SERVICE_SECRET`, and
`FARMVERSE_MQTT_PASSWORD`. Required Gateway secrets include
`GATEWAY_SERVICE_CLIENT_SECRET` and `FARMVERSE_MQTT_PASSWORD`.

Apply manifests only after dependent Identity, Inventory, Finance, PostgreSQL, MQTT,
and their TLS trust are available. Roll back with `kubectl rollout undo deployment/<name>`.
