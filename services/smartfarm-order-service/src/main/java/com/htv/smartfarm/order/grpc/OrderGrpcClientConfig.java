package com.htv.smartfarm.order.grpc;

import com.htv.smartfarm.proto.finance.v1.FarmFinanceServiceGrpc;
import com.htv.smartfarm.proto.inventory.v1.InventoryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OrderGrpcClientConfig {
    private static ManagedChannel channel(String host, int port) {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("gRPC host must not be blank");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("gRPC port is invalid");
        String normalized = host.trim();
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(normalized, port);
        if (loopback(normalized)) builder.usePlaintext(); else builder.useTransportSecurity();
        return builder.build();
    }
    private static boolean loopback(String host) {
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                || host.equals("::1") || host.equals("[::1]");
    }
    public static void close(ManagedChannel channel) {
        channel.shutdown();
        try {
            if (!channel.awaitTermination(10, TimeUnit.SECONDS)) channel.shutdownNow();
        } catch (InterruptedException interrupted) {
            channel.shutdownNow(); Thread.currentThread().interrupt();
        }
    }
    @Bean(name="inventoryChannel", destroyMethod="")
    ManagedChannel inventoryChannel(@Value("${smartfarm.inventory.grpc-host:localhost}") String host,
            @Value("${smartfarm.inventory.grpc-port:9093}") int port) { return channel(host,port); }
    @Bean(name="financeChannel", destroyMethod="")
    ManagedChannel financeChannel(@Value("${smartfarm.finance.grpc-host:localhost}") String host,
            @Value("${smartfarm.finance.grpc-port:9094}") int port) { return channel(host,port); }
    @Bean InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub(@Qualifier("inventoryChannel") ManagedChannel c) { return InventoryServiceGrpc.newBlockingStub(c); }
    @Bean FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub financeStub(@Qualifier("financeChannel") ManagedChannel c) { return FarmFinanceServiceGrpc.newBlockingStub(c); }
    @Bean(name="orderGrpcChannelCloser") AutoCloseable closer(@Qualifier("inventoryChannel") ManagedChannel inventory,
            @Qualifier("financeChannel") ManagedChannel finance) {
        return () -> { close(inventory); close(finance); };
    }
}
