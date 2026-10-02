package com.htv.smartfarm.gateway.grpc;

public class GatewayGraphQlException extends RuntimeException {
    private final String classification;
    public GatewayGraphQlException(String message, String classification, Throwable cause) {
        super(message, cause);
        this.classification = classification;
    }
    public String classification() { return classification; }
}
