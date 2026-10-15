package com.htv.smartfarm.gateway.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;

import com.htv.smartfarm.proto.identity.v1.ExchangeDelegatedTokenRequest;
import com.htv.smartfarm.proto.identity.v1.IdentityTokenServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.TokenGrantResponse;
import com.htv.smartfarm.security.authorization.InMemoryAuthorizationVersionWatermarkStore;

import io.grpc.CallCredentials;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ServiceTokenClientTest {
    @Test
    void exchangesDelegatedTokenWithIdentityOverGrpcAndCachesByVersions() {
        IdentityTokenServiceGrpc.IdentityTokenServiceBlockingStub identity =
                mock(IdentityTokenServiceGrpc.IdentityTokenServiceBlockingStub.class);
        when(identity.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(identity);
        when(identity.withCallCredentials(any(CallCredentials.class))).thenReturn(identity);
        when(identity.exchangeDelegatedToken(any(ExchangeDelegatedTokenRequest.class)))
                .thenReturn(TokenGrantResponse.newBuilder()
                        .setAccessToken("service-jwt")
                        .setTokenType("Bearer")
                        .setExpiresInSeconds(300)
                        .setAudience("smartfarm-order")
                        .build());

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
                .claim("authorization_version", 3)
                .claim("tenant_authorization_version", 7)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThat(client.tokenFor("smartfarm-order", user)).isEqualTo("service-jwt");
        assertThat(client.tokenFor("smartfarm-order", user)).isEqualTo("service-jwt");

        var request = org.mockito.ArgumentCaptor.forClass(ExchangeDelegatedTokenRequest.class);
        verify(identity, times(1)).exchangeDelegatedToken(request.capture());
        assertThat(request.getValue().getClientId()).isEqualTo("gateway");
        assertThat(request.getValue().getClientSecret()).isEqualTo("client-secret");
        assertThat(request.getValue().getActorSubjectId()).isEqualTo("user-1");
        assertThat(request.getValue().getContext().getTenantId()).isEqualTo("tenant-1");
        assertThat(request.getValue().getContext().getActorId()).isEqualTo("user-1");
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
