package com.htv.smartfarm.gateway.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.htv.smartfarm.security.authorization.InMemoryAuthorizationVersionWatermarkStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ServiceTokenClientTest {
    @Test
    void sendsDedicatedInternalSecretHeaderWhenMintingToken() {
        var captured = new AtomicReference<org.springframework.web.reactive.function.client.ClientRequest>();
        WebClient identity = WebClient.builder()
                .baseUrl("http://identity")
                .exchangeFunction(request -> {
                    captured.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body("""
                                    {"accessToken":"service-jwt","tokenType":"Bearer",
                                     "expiresInSeconds":300,"audience":"inventory"}
                                    """)
                            .build());
                })
                .build();
        var client = new ServiceTokenClient(
                identity,
                "gateway",
                "client-secret",
                "internal-transport-secret",
                Duration.ofSeconds(2),
                new InMemoryAuthorizationVersionWatermarkStore()
        );

        assertThat(client.tokenFor("inventory", "tenant-1", "user-1"))
                .isEqualTo("service-jwt");
        assertThat(captured.get().headers().getFirst("X-Identity-Internal-Secret"))
                .isEqualTo("internal-transport-secret");
    }

    @Test
    void refusesToMintWhenInternalSecretIsMissing() {
        var client = new ServiceTokenClient(
                WebClient.builder().baseUrl("http://identity").build(),
                "gateway",
                "client-secret",
                "",
                Duration.ofSeconds(2),
                new InMemoryAuthorizationVersionWatermarkStore()
        );

        assertThatThrownBy(() -> client.tokenFor(
                "inventory",
                "tenant-1",
                "user-1"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("internal API secret");
    }
}
