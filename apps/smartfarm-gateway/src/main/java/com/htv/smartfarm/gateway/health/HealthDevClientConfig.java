package com.htv.smartfarm.gateway.health;

import com.htv.smartfarm.gateway.grpc.GatewayGrpcChannelFactory;
import com.htv.smartfarm.proto.health.v1.AnimalHealthServiceGrpc;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * GW-01: wires the gateway to the health-service gRPC endpoint (AnimalHealthService).
 * Dev-only facade client, mirroring {@code ReportingDevClientConfig}. Health gRPC runs on 9097.
 */
@Configuration(proxyBeanMethods = false)
public class HealthDevClientConfig {
    @Bean(name = "healthChannel", destroyMethod = "shutdown")
    ManagedChannel healthChannel(GatewayGrpcChannelFactory channels,
                                 @Value("${smartfarm.health.grpc-host:localhost}") String host,
                                 @Value("${smartfarm.health.grpc-port:9097}") int port) {
        return channels.create(host, port);
    }

    @Bean
    AnimalHealthServiceGrpc.AnimalHealthServiceBlockingStub healthStub(
            @Qualifier("healthChannel") ManagedChannel channel) {
        return AnimalHealthServiceGrpc.newBlockingStub(channel);
    }
}
