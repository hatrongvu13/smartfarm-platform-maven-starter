package com.htv.smartfarm.gateway.context;

import java.util.UUID;

import reactor.util.context.ContextView;

public final class GatewayCorrelationContext {
    public static final String KEY = GatewayCorrelationContext.class.getName() + ".correlationId";

    private GatewayCorrelationContext() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 128) return UUID.randomUUID().toString();
        return value.trim();
    }

    public static String get(ContextView context) {
        return context.hasKey(KEY) ? normalize(context.get(KEY)) : UUID.randomUUID().toString();
    }
}
