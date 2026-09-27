package com.htv.smartfarm.reporting.grpc;

import com.htv.smartfarm.proto.livestock.v1.LivestockTaskServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC client wiring for the reporting data providers. Plaintext on loopback only (dev).
 * Add more channels/stubs here as more report types pull from their owning service.
 */
@Configuration(proxyBeanMethods = false)
public class ReportingGrpcClientConfig {

    @Bean(name = "livestockChannel", destroyMethod = "shutdown")
    ManagedChannel livestockChannel(@Value("${smartfarm.livestock.grpc-host:localhost}") String host,
                                    @Value("${smartfarm.livestock.grpc-port:9091}") int port) {
        if (!host.equals("localhost") && !host.equals("127.0.0.1"))
            throw new IllegalArgumentException("plaintext gRPC allowed on loopback only");
        return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }

    @Bean
    LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub livestockStub(
            @Qualifier("livestockChannel") ManagedChannel channel) {
        return LivestockTaskServiceGrpc.newBlockingStub(channel);
    }
}
