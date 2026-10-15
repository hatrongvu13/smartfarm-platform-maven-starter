package com.htv.smartfarm.reporting.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.htv.smartfarm.proto.identity.v1.IdentityTokenServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IssueServiceTokenRequest;
import com.htv.smartfarm.proto.identity.v1.TokenGrantResponse;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ServiceTokenClientTest {
    @Test
    void requestsMachineTokenFromIdentityGrpcAndCachesIt() {
        IdentityTokenServiceGrpc.IdentityTokenServiceBlockingStub identity =
                mock(IdentityTokenServiceGrpc.IdentityTokenServiceBlockingStub.class);
        when(identity.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(identity);
        when(identity.issueServiceToken(any(IssueServiceTokenRequest.class)))
                .thenReturn(TokenGrantResponse.newBuilder()
                        .setAccessToken("machine-jwt")
                        .setTokenType("Bearer")
                        .setExpiresInSeconds(300)
                        .setAudience("smartfarm-livestock")
                        .build());
        var client = new ServiceTokenClient(
                identity, "reporting", "client-secret", Duration.ofSeconds(2)
        );

        assertThat(client.tokenFor("smartfarm-livestock", "tenant-1", "reporting"))
                .isEqualTo("machine-jwt");
        assertThat(client.tokenFor("smartfarm-livestock", "tenant-1", "reporting"))
                .isEqualTo("machine-jwt");

        var request = org.mockito.ArgumentCaptor.forClass(IssueServiceTokenRequest.class);
        verify(identity, times(1)).issueServiceToken(request.capture());
        assertThat(request.getValue().getClientId()).isEqualTo("reporting");
        assertThat(request.getValue().getAudience()).isEqualTo("smartfarm-livestock");
        assertThat(request.getValue().getContext().getTenantId()).isEqualTo("tenant-1");
        assertThat(request.getValue().getContext().getActorId()).isEqualTo("reporting");
    }
}
