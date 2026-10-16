package com.htv.smartfarm.order.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.smartfarm.proto.identity.v1.IdentityTokenServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IssueServiceTokenRequest;
import com.htv.smartfarm.proto.identity.v1.TokenGrantResponse;

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
                                .setAudience("smartfarm-inventory")
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
                    "order",
                    "client-secret",
                    Duration.ofSeconds(2)
            );

            assertThat(client.tokenFor("smartfarm-inventory", "tenant-1", "actor-1"))
                    .isEqualTo("machine-jwt");
            assertThat(client.tokenFor("smartfarm-inventory", "tenant-1", "actor-1"))
                    .isEqualTo("machine-jwt");
            assertThat(received.get().getClientId()).isEqualTo("order");
            assertThat(received.get().getClientSecret()).isEqualTo("client-secret");
            assertThat(received.get().getAudience()).isEqualTo("smartfarm-inventory");
            assertThat(received.get().getContext().getTenantId()).isEqualTo("tenant-1");
            assertThat(received.get().getContext().getActorId()).isEqualTo("actor-1");
        } finally {
            channel.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(1, TimeUnit.SECONDS);
        }
    }
}
