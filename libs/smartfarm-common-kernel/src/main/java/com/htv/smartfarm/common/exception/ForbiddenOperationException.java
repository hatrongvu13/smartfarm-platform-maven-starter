package com.htv.smartfarm.common.exception;

public final class ForbiddenOperationException extends BusinessException {
    public ForbiddenOperationException(String code, String message) {
        super(code, message);
    }
}
