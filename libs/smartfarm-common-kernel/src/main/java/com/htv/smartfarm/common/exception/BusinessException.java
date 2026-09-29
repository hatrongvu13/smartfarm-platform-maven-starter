package com.htv.smartfarm.common.exception;

public abstract class BusinessException extends RuntimeException {
    private final String code;

    protected BusinessException(String code, String message) {
        super(requireText(message, "message"));
        this.code = requireText(code, "code");
    }

    protected BusinessException(String code, String message, Throwable cause) {
        super(requireText(message, "message"), cause);
        this.code = requireText(code, "code");
    }

    public String code() {
        return code;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
