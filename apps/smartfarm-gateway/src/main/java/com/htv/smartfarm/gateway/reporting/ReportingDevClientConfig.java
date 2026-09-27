package com.htv.smartfarm.gateway.reporting;

import com.htv.smartfarm.proto.reporting.v1.ReportingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@Profile("dev & !prod")
public class ReportingDevClientConfig {

    @Bean(name = "reportingChannel", destroyMethod = "shutdown")
    ManagedChannel reportingChannel(@Value("${smartfarm.reporting.grpc-host:localhost}") String host,
                                    @Value("${smartfarm.reporting.grpc-port:9096}") int port) {
        if (!host.equals("localhost") && !host.equals("127.0.0.1"))
            throw new IllegalArgumentException("plaintext gRPC allowed on loopback only");
        return ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    }

    @Bean
    ReportingServiceGrpc.ReportingServiceBlockingStub reportingStub(@Qualifier("reportingChannel") ManagedChannel channel) {
        return ReportingServiceGrpc.newBlockingStub(channel);
    }
}
