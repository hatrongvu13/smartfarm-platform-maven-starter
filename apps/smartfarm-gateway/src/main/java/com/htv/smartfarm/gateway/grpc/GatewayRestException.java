package com.htv.smartfarm.gateway.grpc;

import org.springframework.http.HttpStatus;

public final class GatewayRestException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public GatewayRestException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
