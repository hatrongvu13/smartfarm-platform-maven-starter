package com.htv.smartfarm.common.exception;

import java.util.Locale;

public final class NotFoundException extends BusinessException {
    public NotFoundException(String code, String message) {
        super(code, message);
    }

    public static NotFoundException entity(String entityName, String identifier) {
        String name = requireText(entityName, "entityName");
        String id = requireText(identifier, "identifier");
        return new NotFoundException(name.toUpperCase(Locale.ROOT) + "_NOT_FOUND",
                name + " not found: " + id);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
