package com.htv.smartfarm.gateway.order;

import com.htv.smartfarm.proto.inventory.v1.InventoryServiceGrpc;
import com.htv.smartfarm.proto.finance.v1.FarmFinanceServiceGrpc;
import com.htv.smartfarm.proto.order.v1.FarmOrderServiceGrpc;
import com.htv.smartfarm.proto.order.v1.OrderSagaAdministrationServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DEV-only gRPC client wiring so the gateway can expose thin REST endpoints for order + inventory,
 * used by the Phase 3 saga curl test. Plaintext on loopback only; production traffic uses TLS and
 * never opens these dev controllers. Each call attaches a per-service token (Phase 2).
 */
@Configuration(proxyBeanMethods = false)
public class OrderDevClientConfig {

    private static ManagedChannel channel(String host, int port) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        if (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1")) builder.usePlaintext();
        else builder.useTransportSecurity();
        return builder.build();
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
    OrderSagaAdministrationServiceGrpc.OrderSagaAdministrationServiceBlockingStub orderSagaAdminStub(
            @Qualifier("orderChannel") ManagedChannel channel) {
        return OrderSagaAdministrationServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    InventoryServiceGrpc.InventoryServiceBlockingStub gwInventoryStub(@Qualifier("gwInventoryChannel") ManagedChannel channel) {
        return InventoryServiceGrpc.newBlockingStub(channel);
    }

    @Bean(name = "gwFinanceChannel", destroyMethod = "shutdown")
    ManagedChannel gwFinanceChannel(@Value("${smartfarm.finance.grpc-host:localhost}") String host,
                                    @Value("${smartfarm.finance.grpc-port:9094}") int port) {
        return channel(host, port);
    }

    @Bean
    FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub gwFinanceStub(@Qualifier("gwFinanceChannel") ManagedChannel channel) {
        return FarmFinanceServiceGrpc.newBlockingStub(channel);
    }
}
