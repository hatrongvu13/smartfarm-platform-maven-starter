package com.htv.farmverse.reporting.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.farmverse.proto.identity.v1.IdentityTokenServiceGrpc;
import com.htv.farmverse.proto.identity.v1.IssueServiceTokenRequest;
import com.htv.farmverse.proto.identity.v1.TokenGrantResponse;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

class ServiceTokenClientTest {
    @Test
    void requestsMachineTokenFromIdentityGrpcAndCachesIt() throws Exception {
        var received = new AtomicReference<IssueServiceTokenRequest>();
        Server server = ServerBuilder.forPort(0)
                .addService(new IdentityTokenServiceGrpc.IdentityTokenServiceImplBase() {
                    @Override
                    public void issueServiceToken(
                            IssueServiceTokenRequest request,
                            StreamObserver<TokenGrantResponse> response
                    ) {
                        received.set(request);
                        response.onNext(TokenGrantResponse.newBuilder()
                                .setAccessToken("machine-jwt")
                                .setTokenType("Bearer")
                                .setExpiresInSeconds(300)
                                .setAudience("farmverse-livestock")
                                .build());
                        response.onCompleted();
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
                    "reporting",
                    "client-secret",
                    Duration.ofSeconds(2)
            );

            assertThat(client.tokenFor("farmverse-livestock", "tenant-1", "reporting"))
                    .isEqualTo("machine-jwt");
            assertThat(client.tokenFor("farmverse-livestock", "tenant-1", "reporting"))
                    .isEqualTo("machine-jwt");
            assertThat(received.get().getClientId()).isEqualTo("reporting");
            assertThat(received.get().getClientSecret()).isEqualTo("client-secret");
            assertThat(received.get().getAudience()).isEqualTo("farmverse-livestock");
            assertThat(received.get().getContext().getTenantId()).isEqualTo("tenant-1");
            assertThat(received.get().getContext().getActorId()).isEqualTo("reporting");
        } finally {
            channel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
        }
    }
}
