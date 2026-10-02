package com.htv.smartfarm.gateway.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.stereotype.Component;

@Component
public class GatewayGrpcChannelFactory {
    public ManagedChannel create(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("gRPC host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("gRPC port is invalid");
        }
        String normalized = host.trim();
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(normalized, port);
        if (loopback(normalized)) builder.usePlaintext();
        else builder.useTransportSecurity();
        return builder.build();
    }

    private boolean loopback(String host) {
        return host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.equals("[::1]");
    }
}
