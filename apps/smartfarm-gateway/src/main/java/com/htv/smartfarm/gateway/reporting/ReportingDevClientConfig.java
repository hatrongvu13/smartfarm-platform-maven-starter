package com.htv.smartfarm.gateway.reporting;

import com.htv.smartfarm.gateway.grpc.GatewayGrpcChannelFactory;
import com.htv.smartfarm.proto.reporting.v1.ReportingServiceGrpc;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("dev & !prod")
public class ReportingDevClientConfig {
    @Bean(name = "reportingChannel", destroyMethod = "shutdown")
    ManagedChannel reportingChannel(GatewayGrpcChannelFactory channels,
            @Value("${smartfarm.reporting.grpc-host:localhost}") String host,
            @Value("${smartfarm.reporting.grpc-port:9096}") int port) {
        return channels.create(host, port);
    }
    @Bean ReportingServiceGrpc.ReportingServiceBlockingStub reportingStub(@Qualifier("reportingChannel") ManagedChannel channel) {
        return ReportingServiceGrpc.newBlockingStub(channel);
    }
}
