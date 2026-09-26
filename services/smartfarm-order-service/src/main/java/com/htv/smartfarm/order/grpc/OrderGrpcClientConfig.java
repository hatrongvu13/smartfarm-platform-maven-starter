package com.htv.smartfarm.order.grpc;

import com.htv.smartfarm.proto.finance.v1.FarmFinanceServiceGrpc;
import com.htv.smartfarm.proto.inventory.v1.InventoryServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC client wiring for the saga owner. Plaintext is permitted on loopback only (dev);
 * production must use TLS. Each downstream call attaches a per-service token
 * (see {@link ServiceTokenClient}) scoped to that service's audience.
 */
@Configuration(proxyBeanMethods = false)
public class OrderGrpcClientConfig {

    private static ManagedChannel channel(String host, int port) {
        if (!host.equals("localhost") && !host.equals("127.0.0.1"))
            throw new IllegalArgumentException("plaintext gRPC allowed on loopback only");
        return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }

    @Bean(name = "inventoryChannel", destroyMethod = "shutdown")
    ManagedChannel inventoryChannel(@Value("${smartfarm.inventory.grpc-host:localhost}") String host,
                                    @Value("${smartfarm.inventory.grpc-port:9093}") int port) {
        return channel(host, port);
    }

    @Bean(name = "financeChannel", destroyMethod = "shutdown")
    ManagedChannel financeChannel(@Value("${smartfarm.finance.grpc-host:localhost}") String host,
                                  @Value("${smartfarm.finance.grpc-port:9094}") int port) {
        return channel(host, port);
    }

    @Bean
    InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub(
            @org.springframework.beans.factory.annotation.Qualifier("inventoryChannel") ManagedChannel channel) {
        return InventoryServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub financeStub(
            @org.springframework.beans.factory.annotation.Qualifier("financeChannel") ManagedChannel channel) {
        return FarmFinanceServiceGrpc.newBlockingStub(channel);
    }
}
