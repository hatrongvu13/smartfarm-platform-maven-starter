package com.htv.smartfarm.gateway.livestock;

import com.htv.smartfarm.gateway.grpc.GatewayGrpcChannelFactory;
import com.htv.smartfarm.proto.livestock.v1.LivestockTaskServiceGrpc;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods = false)
public class LivestockDevClientConfig {
    @Bean(name = "livestockChannel", destroyMethod = "shutdown")
    ManagedChannel livestockChannel(GatewayGrpcChannelFactory channels,
            @Value("${smartfarm.livestock.grpc-host:localhost}") String host,
            @Value("${smartfarm.livestock.grpc-port:9091}") int port) {
        return channels.create(host, port);
    }
    @Bean LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub livestockStub(@Qualifier("livestockChannel") ManagedChannel channel) {
        return LivestockTaskServiceGrpc.newBlockingStub(channel);
    }
}
