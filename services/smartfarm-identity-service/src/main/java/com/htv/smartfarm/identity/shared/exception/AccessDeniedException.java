package com.htv.smartfarm.identity.shared.exception;

public class AccessDeniedException extends RuntimeException {

    private final String reasonCode;

    public AccessDeniedException(
            String reasonCode,
            String message
    ) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}