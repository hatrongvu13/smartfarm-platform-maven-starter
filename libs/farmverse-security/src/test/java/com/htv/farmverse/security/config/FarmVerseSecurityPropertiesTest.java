package com.htv.farmverse.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FarmVerseSecurityPropertiesTest {

    @Test
    void shouldAcceptHttpsConfiguration() {
        var jwt = new FarmVerseSecurityProperties.Jwt(
                URI.create("https://identity.farmverse.vn"),
                URI.create("https://identity.farmverse.vn/oauth2/jwks"),
                Set.of("api-gateway"),
                false
        );

        var properties = new FarmVerseSecurityProperties(
                jwt,
                null,
                null
        );

        assertThat(properties.grpc().isEnabled()).isTrue();
        assertThat(properties.grpc().defaultDeadline())
                .isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.correlation().headerName())
                .isEqualTo("x-correlation-id");
    }

    @Test
    void shouldAcceptSameLoopbackEndpointWhenExplicitlyAllowed() {
        var jwt = new FarmVerseSecurityProperties.Jwt(
                URI.create("http://localhost:8092"),
                URI.create("http://localhost:8092/.well-known/jwks.json"),
                Set.of("api-gateway"),
                true
        );

        assertThat(jwt.allowLocalHttp()).isTrue();
    }

    @Test
    void shouldAcceptIpv6Loopback() {
        var jwt = new FarmVerseSecurityProperties.Jwt(
                URI.create("http://[::1]:8092"),
                URI.create("http://[::1]:8092/.well-known/jwks.json"),
                Set.of("api-gateway"),
                true
        );

        assertThat(jwt.issuer().getHost()).contains("::1");
    }

    @Test
    void shouldRejectRemoteHttp() {
        assertThatThrownBy(() ->
                new FarmVerseSecurityProperties.Jwt(
                        URI.create("http://identity.internal:8092"),
                        URI.create(
                                "http://identity.internal:8092/jwks.json"
                        ),
                        Set.of("api-gateway"),
                        true
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDifferentLoopbackPorts() {
        assertThatThrownBy(() ->
                new FarmVerseSecurityProperties.Jwt(
                        URI.create("http://localhost:8092"),
                        URI.create("http://localhost:9092/jwks.json"),
                        Set.of("api-gateway"),
                        true
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectEmptyAudienceSet() {
        assertThatThrownBy(() ->
                new FarmVerseSecurityProperties.Jwt(
                        URI.create("https://identity.farmverse.vn"),
                        URI.create(
                                "https://identity.farmverse.vn/jwks.json"
                        ),
                        Set.of(),
                        false
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidCorrelationHeader() {
        assertThatThrownBy(() ->
                new FarmVerseSecurityProperties.Correlation(
                        "Invalid Header",
                        128,
                        true
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveGrpcDeadline() {
        assertThatThrownBy(() ->
                new FarmVerseSecurityProperties.Grpc(
                        true,
                        true,
                        true,
                        Duration.ZERO
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
