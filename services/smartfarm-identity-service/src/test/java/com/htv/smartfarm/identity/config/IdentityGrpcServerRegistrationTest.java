package com.htv.smartfarm.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.htv.smartfarm.identity.grpc.GrpcExceptionMapper;
import com.htv.smartfarm.identity.grpc.IdentityAdministrationGrpcService;
import com.htv.smartfarm.identity.grpc.IdentityCredentialGrpcService;
import com.htv.smartfarm.identity.grpc.IdentityDirectoryGrpcService;
import com.htv.smartfarm.identity.grpc.IdentityTokenGrpcService;
import com.htv.smartfarm.identity.grpc.PlatformAuthorizationAdministrationGrpcService;
import com.htv.smartfarm.identity.grpc.security.GrpcCallerProvider;
import com.htv.smartfarm.identity.token.ServiceTokenService;
import com.htv.smartfarm.identity.token.session.RefreshSessionAdministrationService;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.identity.v1.IdentityAdministrationServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IdentityCredentialServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IdentityDirectoryServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IdentityTokenServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.ExchangeDelegatedTokenRequest;
import com.htv.smartfarm.proto.identity.v1.IssueServiceTokenRequest;
import com.htv.smartfarm.proto.identity.v1.PlatformAuthorizationAdministrationServiceGrpc;
import com.htv.smartfarm.security.TokenVerifier;
import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;
import com.htv.smartfarm.security.grpc.JwtServerInterceptor;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.NettyGrpcServerFactory;
import org.springframework.grpc.server.service.DefaultGrpcServiceConfigurer;
import org.springframework.grpc.server.service.DefaultGrpcServiceDiscoverer;
import org.springframework.grpc.server.service.GrpcServiceSpec;

class IdentityGrpcServerRegistrationTest {

    private static final List<Class<? extends BindableService>> SERVICES =
            List.of(
                    IdentityDirectoryGrpcService.class,
                    IdentityCredentialGrpcService.class,
                    IdentityAdministrationGrpcService.class,
                    PlatformAuthorizationAdministrationGrpcService.class,
                    IdentityTokenGrpcService.class
            );

    @Test
    void registersEveryIdentityServiceAndAllTokenRpcMethods() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            for (Class<? extends BindableService> serviceType : SERVICES) {
                if (serviceType == IdentityTokenGrpcService.class) {
                    continue;
                }
                context.getBeanFactory().registerSingleton(
                        serviceType.getSimpleName(),
                        instantiateWithMocks(serviceType)
                );
            }
            ServiceTokenService tokenService = mock(ServiceTokenService.class);
            when(tokenService.issue(
                    "gateway",
                    "client-secret",
                    "smartfarm-order",
                    "tenant-1",
                    null
            )).thenReturn(new ServiceTokenService.Grant(
                    "signed-service-token",
                    "Bearer",
                    300,
                    "smartfarm-order"
            ));
            context.getBeanFactory().registerSingleton(
                    "identityTokenGrpcService",
                    new IdentityTokenGrpcService(
                            tokenService,
                            mock(RefreshSessionAdministrationService.class),
                            new GrpcExceptionMapper(),
                            mock(GrpcCallerProvider.class)
                    )
            );
            context.register(
                    IdentityGrpcSecurityConfiguration.class,
                    RegistrationSecurityConfiguration.class
            );
            context.refresh();

            var discoverer = new DefaultGrpcServiceDiscoverer(context);
            var factory = new NettyGrpcServerFactory(
                    "127.0.0.1:0",
                    List.of(),
                    null,
                    null,
                    null
            );
            var configurer = new DefaultGrpcServiceConfigurer(context);
            configurer.afterPropertiesSet();

            List<GrpcServiceSpec> discovered = discoverer.findServices();
            discovered.forEach(spec ->
                    factory.addService(configurer.configure(spec, factory))
            );

            Server server = factory.createServer().start();
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();
            try {
                assertThat(server.getPort()).isPositive();
                Set<String> registeredNames = server.getServices()
                        .stream()
                        .map(service ->
                                service.getServiceDescriptor().getName())
                        .collect(Collectors.toSet());
                assertThat(registeredNames).containsExactlyInAnyOrder(
                        IdentityDirectoryServiceGrpc.getServiceDescriptor()
                                .getName(),
                        IdentityCredentialServiceGrpc.getServiceDescriptor()
                                .getName(),
                        IdentityAdministrationServiceGrpc.getServiceDescriptor()
                                .getName(),
                        PlatformAuthorizationAdministrationServiceGrpc
                                .getServiceDescriptor().getName(),
                        IdentityTokenServiceGrpc.getServiceDescriptor()
                                .getName()
                );

                ServerServiceDefinition tokenDefinition = server.getServices()
                        .stream()
                        .filter(service ->
                                service.getServiceDescriptor().getName()
                                        .equals(IdentityTokenServiceGrpc
                                                .getServiceDescriptor()
                                                .getName()))
                        .findFirst()
                        .orElseThrow();
                Set<String> tokenMethods = tokenDefinition.getMethods()
                        .stream()
                        .map(method -> method.getMethodDescriptor()
                                .getBareMethodName())
                        .collect(Collectors.toSet());
                assertThat(tokenMethods).containsExactlyInAnyOrder(
                        "IssueServiceToken",
                        "ExchangeDelegatedToken",
                        "ListSessions",
                        "RevokeSession",
                        "RevokeAllSessions"
                );

                var stub = IdentityTokenServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(5, TimeUnit.SECONDS);
                var issued = stub.issueServiceToken(IssueServiceTokenRequest
                        .newBuilder()
                        .setClientId("gateway")
                        .setClientSecret("client-secret")
                        .setAudience("smartfarm-order")
                        .setContext(RequestContext.newBuilder()
                                .setTenantId("tenant-1"))
                        .build());
                assertThat(issued.getAccessToken())
                        .isEqualTo("signed-service-token");
                verify(tokenService).issue(
                        "gateway",
                        "client-secret",
                        "smartfarm-order",
                        "tenant-1",
                        null
                );

                assertThatThrownBy(() -> stub.exchangeDelegatedToken(
                        ExchangeDelegatedTokenRequest.newBuilder()
                                .setClientId("gateway")
                                .setClientSecret("client-secret")
                                .setAudience("smartfarm-order")
                                .setActorSubjectId("user-1")
                                .setContext(RequestContext.newBuilder()
                                        .setTenantId("tenant-1"))
                                .build()
                ))
                        .isInstanceOf(StatusRuntimeException.class)
                        .extracting(error -> ((StatusRuntimeException) error)
                                .getStatus().getCode())
                        .isEqualTo(Status.Code.UNAUTHENTICATED);
            } finally {
                channel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
                server.shutdownNow();
                server.awaitTermination(1, TimeUnit.SECONDS);
            }
        }
    }

    private static BindableService instantiateWithMocks(
            Class<? extends BindableService> serviceType
    ) throws Exception {
        Constructor<?> constructor = serviceType.getDeclaredConstructors()[0];
        Object[] dependencies = Arrays.stream(constructor.getParameterTypes())
                .map(IdentityGrpcServerRegistrationTest::mockDependency)
                .toArray();
        return (BindableService) constructor.newInstance(dependencies);
    }

    private static Object mockDependency(Class<?> dependencyType) {
        return mock(dependencyType);
    }

    @Configuration(proxyBeanMethods = false)
    static class RegistrationSecurityConfiguration {

        @Bean
        TokenVerifier registrationTokenVerifier() {
            return mock(TokenVerifier.class);
        }

        @Bean
        @GlobalServerInterceptor
        JwtServerInterceptor registrationJwtServerInterceptor(
                TokenVerifier verifier,
                GrpcMethodPolicy policy
        ) {
            return new JwtServerInterceptor(verifier, policy);
        }
    }
}
