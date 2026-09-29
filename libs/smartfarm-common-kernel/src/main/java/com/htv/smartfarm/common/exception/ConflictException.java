package com.htv.smartfarm.common.exception;

public final class ConflictException extends BusinessException {
    public ConflictException(String code, String message) {
        super(code, message);
    }

    public ConflictException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
