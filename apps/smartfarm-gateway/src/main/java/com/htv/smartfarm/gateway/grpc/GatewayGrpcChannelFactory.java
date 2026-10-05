package com.htv.smartfarm.gateway.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GatewayGrpcChannelFactory {
    private final long shutdownTimeoutMillis;
    public GatewayGrpcChannelFactory(@Value("${smartfarm.gateway.grpc.channel-shutdown-timeout:10s}") Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("channel shutdown timeout must be positive");
        this.shutdownTimeoutMillis = timeout.toMillis();
    }
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


    public void close(ManagedChannel channel) {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(shutdownTimeoutMillis, TimeUnit.MILLISECONDS)) channel.shutdownNow();
        } catch (InterruptedException interrupted) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private boolean loopback(String host) {
        return host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.equals("[::1]");
    }
}
