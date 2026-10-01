package com.htv.smartfarm.gateway.identity;

import com.htv.smartfarm.proto.identity.v1.IdentityAdministrationServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IdentityDirectoryServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IdentityCredentialServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.PlatformAuthorizationAdministrationServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class IdentityGrpcClientConfig {
    @Bean(name="identityGrpcChannel", destroyMethod="shutdown")
    ManagedChannel channel(@Value("${smartfarm.identity.grpc-host:localhost}") String host,
                           @Value("${smartfarm.identity.grpc-port:9092}") int port) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forAddress(host, port);
        if (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1")) builder.usePlaintext();
        else builder.useTransportSecurity();
        return builder.build();
    }
    @Bean IdentityDirectoryServiceGrpc.IdentityDirectoryServiceBlockingStub directory(@Qualifier("identityGrpcChannel") ManagedChannel c) { return IdentityDirectoryServiceGrpc.newBlockingStub(c); }
    @Bean IdentityAdministrationServiceGrpc.IdentityAdministrationServiceBlockingStub administration(@Qualifier("identityGrpcChannel") ManagedChannel c) { return IdentityAdministrationServiceGrpc.newBlockingStub(c); }
    @Bean IdentityCredentialServiceGrpc.IdentityCredentialServiceBlockingStub credential(@Qualifier("identityGrpcChannel") ManagedChannel c) { return IdentityCredentialServiceGrpc.newBlockingStub(c); }
    @Bean PlatformAuthorizationAdministrationServiceGrpc.PlatformAuthorizationAdministrationServiceBlockingStub platform(@Qualifier("identityGrpcChannel") ManagedChannel c) { return PlatformAuthorizationAdministrationServiceGrpc.newBlockingStub(c); }
}
