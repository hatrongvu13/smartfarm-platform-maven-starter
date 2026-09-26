package com.htv.smartfarm.gateway.livestock;
import com.htv.smartfarm.proto.livestock.v1.LivestockTaskServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
@Profile("dev & !prod")
public class LivestockDevClientConfig {
    @Bean(name="livestockChannel", destroyMethod="shutdown") ManagedChannel livestockChannel(@Value("${smartfarm.livestock.grpc-host:localhost}") String host,
                                                                    @Value("${smartfarm.livestock.grpc-port:9091}") int port){
        if(!host.equals("localhost")&&!host.equals("127.0.0.1"))throw new IllegalArgumentException("plaintext gRPC allowed on loopback only");
        return ManagedChannelBuilder.forAddress(host,port).usePlaintext().build();
    }
    @Bean LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub livestockStub(@Qualifier("livestockChannel") ManagedChannel channel){
        return LivestockTaskServiceGrpc.newBlockingStub(channel);
    }
}
