package com.htv.smartfarm.gateway.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.htv.smartfarm.proto.identity.v1.ExchangeDelegatedTokenRequest;
import com.htv.smartfarm.proto.identity.v1.IdentityTokenServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.TokenGrantResponse;
import com.htv.smartfarm.security.authorization.InMemoryAuthorizationVersionWatermarkStore;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ServiceTokenClientTest {
    @Test
    void exchangesDelegatedTokenWithIdentityOverGrpcAndCachesByVersions() throws Exception {
        var received = new AtomicReference<ExchangeDelegatedTokenRequest>();
        var authorization = new AtomicReference<String>();
        Metadata.Key<String> authorizationKey = Metadata.Key.of(
                "authorization",
                Metadata.ASCII_STRING_MARSHALLER
        );
        Server server = ServerBuilder.forPort(0)
                .addService(new IdentityTokenServiceGrpc.IdentityTokenServiceImplBase() {
                    @Override
                    public void exchangeDelegatedToken(
                            ExchangeDelegatedTokenRequest request,
                            StreamObserver<TokenGrantResponse> response
                    ) {
                        received.set(request);
                        response.onNext(TokenGrantResponse.newBuilder()
                                .setAccessToken("service-jwt")
                                .setTokenType("Bearer")
                                .setExpiresInSeconds(300)
                                .setAudience("smartfarm-order")
                                .build());
                        response.onCompleted();
                    }
                })
                .intercept(new ServerInterceptor() {
                    @Override
                    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                            ServerCall<ReqT, RespT> call,
                            Metadata headers,
                            ServerCallHandler<ReqT, RespT> next
                    ) {
                        authorization.set(headers.get(authorizationKey));
                        return next.startCall(call, headers);
                    }
                })
                .build()
                .start();
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .build();
        try {
            var client = new ServiceTokenClient(
                    IdentityTokenServiceGrpc.newBlockingStub(channel),
                    "gateway",
                    "client-secret",
                    Duration.ofSeconds(2),
                    new InMemoryAuthorizationVersionWatermarkStore()
            );
            Jwt user = Jwt.withTokenValue("user-jwt")
                    .header("alg", "RS256")
                    .subject("user-1")
                    .claim("tenant_id", "tenant-1")
                    .claim("authorization_version", 3)
                    .claim("tenant_authorization_version", 7)
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();

            assertThat(client.tokenFor("smartfarm-order", user)).isEqualTo("service-jwt");
            assertThat(client.tokenFor("smartfarm-order", user)).isEqualTo("service-jwt");
            assertThat(received.get().getClientId()).isEqualTo("gateway");
            assertThat(received.get().getClientSecret()).isEqualTo("client-secret");
            assertThat(received.get().getActorSubjectId()).isEqualTo("user-1");
            assertThat(received.get().getContext().getTenantId()).isEqualTo("tenant-1");
            assertThat(received.get().getContext().getActorId()).isEqualTo("user-1");
            assertThat(authorization.get()).isEqualTo("Bearer user-jwt");
        } finally {
            channel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectsJwtWithoutAuthorizationVersions() {
        var identity = mock(IdentityTokenServiceGrpc.IdentityTokenServiceBlockingStub.class);
        var client = new ServiceTokenClient(
                identity,
                "gateway",
                "client-secret",
                Duration.ofSeconds(2),
                new InMemoryAuthorizationVersionWatermarkStore()
        );
        Jwt user = Jwt.withTokenValue("user-jwt")
                .header("alg", "RS256")
                .subject("user-1")
                .claim("tenant_id", "tenant-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> client.tokenFor("smartfarm-order", user)
        ).isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("authorization_version");
        verifyNoInteractions(identity);
    }
}
