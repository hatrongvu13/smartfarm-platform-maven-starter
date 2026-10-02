package com.htv.smartfarm.gateway.order;

import com.htv.smartfarm.gateway.grpc.GatewayGrpcChannelFactory;
import com.htv.smartfarm.proto.finance.v1.FarmFinanceServiceGrpc;
import com.htv.smartfarm.proto.inventory.v1.InventoryServiceGrpc;
import com.htv.smartfarm.proto.order.v1.FarmOrderServiceGrpc;
import com.htv.smartfarm.proto.order.v1.OrderSagaAdministrationServiceGrpc;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OrderGrpcClientConfiguration {
    @Bean(name = "orderChannel", destroyMethod = "")
    ManagedChannel orderChannel(GatewayGrpcChannelFactory channels,
            @Value("${smartfarm.order.grpc-host:localhost}") String host,
            @Value("${smartfarm.order.grpc-port:9095}") int port) {
        return channels.create(host, port);
    }
    @Bean(name = "gwInventoryChannel", destroyMethod = "")
    ManagedChannel gwInventoryChannel(GatewayGrpcChannelFactory channels,
            @Value("${smartfarm.inventory.grpc-host:localhost}") String host,
            @Value("${smartfarm.inventory.grpc-port:9093}") int port) {
        return channels.create(host, port);
    }
    @Bean(name = "gwFinanceChannel", destroyMethod = "")
    ManagedChannel gwFinanceChannel(GatewayGrpcChannelFactory channels,
            @Value("${smartfarm.finance.grpc-host:localhost}") String host,
            @Value("${smartfarm.finance.grpc-port:9094}") int port) {
        return channels.create(host, port);
    }
    @Bean FarmOrderServiceGrpc.FarmOrderServiceBlockingStub orderStub(@Qualifier("orderChannel") ManagedChannel channel) { return FarmOrderServiceGrpc.newBlockingStub(channel); }
    @Bean OrderSagaAdministrationServiceGrpc.OrderSagaAdministrationServiceBlockingStub orderSagaAdminStub(@Qualifier("orderChannel") ManagedChannel channel) { return OrderSagaAdministrationServiceGrpc.newBlockingStub(channel); }
    @Bean InventoryServiceGrpc.InventoryServiceBlockingStub gwInventoryStub(@Qualifier("gwInventoryChannel") ManagedChannel channel) { return InventoryServiceGrpc.newBlockingStub(channel); }
    @Bean FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub gwFinanceStub(@Qualifier("gwFinanceChannel") ManagedChannel channel) { return FarmFinanceServiceGrpc.newBlockingStub(channel); }
    @Bean(name = "gatewayOrderChannelsCloser") AutoCloseable gatewayOrderChannelsCloser(
            GatewayGrpcChannelFactory channels,
            @Qualifier("orderChannel") ManagedChannel order,
            @Qualifier("gwInventoryChannel") ManagedChannel inventory,
            @Qualifier("gwFinanceChannel") ManagedChannel finance) {
        return () -> { channels.close(order); channels.close(inventory); channels.close(finance); };
    }
}
