package com.htv.farmverse.reporting.grpc;

import com.htv.farmverse.proto.livestock.v1.LivestockTaskServiceGrpc;
import com.htv.farmverse.proto.identity.v1.IdentityTokenServiceGrpc;

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

    @Bean(name = "livestockChannel", destroyMethod = "")
    ManagedChannel livestockChannel(@Value("${farmverse.livestock.grpc-host:localhost}") String host,
                                    @Value("${farmverse.livestock.grpc-port:9091}") int port) {
        if (!host.equals("localhost") && !host.equals("127.0.0.1"))
            throw new IllegalArgumentException("plaintext gRPC allowed on loopback only");
        return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }

    @Bean(name = "identityChannel", destroyMethod = "")
    ManagedChannel identityChannel(@Value("${farmverse.identity.grpc-host:localhost}") String host,
                                   @Value("${farmverse.identity.grpc-port:9092}") int port) {
        if (!host.equals("localhost") && !host.equals("127.0.0.1"))
            throw new IllegalArgumentException("plaintext gRPC allowed on loopback only");
        return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }

    @Bean
    LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub livestockStub(
            @Qualifier("livestockChannel") ManagedChannel channel) {
        return LivestockTaskServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    IdentityTokenServiceGrpc.IdentityTokenServiceBlockingStub identityTokenStub(
            @Qualifier("identityChannel") ManagedChannel channel) {
        return IdentityTokenServiceGrpc.newBlockingStub(channel);
    }

    @Bean(name = "reportingGrpcChannelCloser")
    AutoCloseable closeChannels(@Qualifier("livestockChannel") ManagedChannel livestock,
                                @Qualifier("identityChannel") ManagedChannel identity) {
        return () -> {
            livestock.shutdown();
            identity.shutdown();
            try {
                if (!livestock.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    livestock.shutdownNow();
                }
                if (!identity.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    identity.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                livestock.shutdownNow();
                identity.shutdownNow();
                Thread.currentThread().interrupt();
            }
        };
    }
}
