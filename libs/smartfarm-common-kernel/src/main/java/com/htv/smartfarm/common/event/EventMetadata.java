package com.htv.smartfarm.common.event;

import com.htv.smartfarm.common.context.RequestMetadata;

public record EventMetadata(String correlationId, String traceId, String tenantId, String actorId,
                            String sourceService) {
    public EventMetadata {
        correlationId = normalizeNullable(correlationId);
        traceId = normalizeNullable(traceId);
        tenantId = normalizeNullable(tenantId);
        actorId = normalizeNullable(actorId);
        sourceService = requireText(sourceService, "sourceService");
        validateMaximumLength(correlationId, "correlationId", 128);
        validateMaximumLength(traceId, "traceId", 128);
        validateMaximumLength(tenantId, "tenantId", 100);
        validateMaximumLength(actorId, "actorId", 100);
        validateMaximumLength(sourceService, "sourceService", 100);
    }

    public static EventMetadata from(RequestMetadata requestMetadata, String sourceService) {
        if (requestMetadata == null) {
            return new EventMetadata(null, null, null, null, sourceService);
        }
        return new EventMetadata(requestMetadata.correlationId(), requestMetadata.traceId(), requestMetadata.tenantId(), requestMetadata.actorId(), sourceService);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static void validateMaximumLength(String value, String field, int maximumLength) {
        if (value != null && value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maximumLength + " characters");
        }
    }
}
