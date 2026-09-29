package com.htv.smartfarm.common.context;

public record RequestMetadata(
        String tenantId,
        String actorId,
        String correlationId,
        String traceId,
        String idempotencyKey
) {
    private static final int MAX_TENANT_ID_LENGTH = 100;
    private static final int MAX_ACTOR_ID_LENGTH = 100;
    private static final int MAX_CORRELATION_ID_LENGTH = 128;
    private static final int MAX_TRACE_ID_LENGTH = 128;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 200;

    public RequestMetadata {
        tenantId = normalizeNullable(tenantId);
        actorId = normalizeNullable(actorId);
        correlationId = normalizeNullable(correlationId);
        traceId = normalizeNullable(traceId);
        idempotencyKey = normalizeNullable(idempotencyKey);
        validateMaximumLength(tenantId, "tenantId", MAX_TENANT_ID_LENGTH);
        validateMaximumLength(actorId, "actorId", MAX_ACTOR_ID_LENGTH);
        validateMaximumLength(correlationId, "correlationId", MAX_CORRELATION_ID_LENGTH);
        validateMaximumLength(traceId, "traceId", MAX_TRACE_ID_LENGTH);
        validateMaximumLength(idempotencyKey, "idempotencyKey", MAX_IDEMPOTENCY_KEY_LENGTH);
    }

    public static RequestMetadata empty() {
        return new RequestMetadata(null, null, null, null, null);
    }

    public boolean hasTenant() {
        return tenantId != null;
    }

    public boolean hasActor() {
        return actorId != null;
    }

    public boolean hasCorrelationId() {
        return correlationId != null;
    }

    public boolean hasTraceId() {
        return traceId != null;
    }

    public boolean hasIdempotencyKey() {
        return idempotencyKey != null;
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateMaximumLength(String value, String field, int maximumLength) {
        if (value != null && value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maximumLength + " characters");
        }
    }
}
