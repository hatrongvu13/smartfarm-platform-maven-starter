package com.htv.smartfarm.gateway.order;

import com.htv.smartfarm.proto.inventory.v1.InventoryServiceGrpc;
import com.htv.smartfarm.proto.order.v1.FarmOrderServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * DEV-only gRPC client wiring so the gateway can expose thin REST endpoints for order + inventory,
 * used by the Phase 3 saga curl test. Plaintext on loopback only; production traffic uses TLS and
 * never opens these dev controllers. Each call attaches a per-service token (Phase 2).
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev & !prod")
public class OrderDevClientConfig {

    private static ManagedChannel channel(String host, int port) {
        if (!host.equals("localhost") && !host.equals("127.0.0.1"))
            throw new IllegalArgumentException("plaintext gRPC allowed on loopback only");
        return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }

    @Bean(name = "orderChannel", destroyMethod = "shutdown")
    ManagedChannel orderChannel(@Value("${smartfarm.order.grpc-host:localhost}") String host,
                                @Value("${smartfarm.order.grpc-port:9095}") int port) {
        return channel(host, port);
    }

    @Bean(name = "gwInventoryChannel", destroyMethod = "shutdown")
    ManagedChannel gwInventoryChannel(@Value("${smartfarm.inventory.grpc-host:localhost}") String host,
                                      @Value("${smartfarm.inventory.grpc-port:9093}") int port) {
        return channel(host, port);
    }

    @Bean
    FarmOrderServiceGrpc.FarmOrderServiceBlockingStub orderStub(@Qualifier("orderChannel") ManagedChannel channel) {
        return FarmOrderServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    InventoryServiceGrpc.InventoryServiceBlockingStub gwInventoryStub(@Qualifier("gwInventoryChannel") ManagedChannel channel) {
        return InventoryServiceGrpc.newBlockingStub(channel);
    }
}
