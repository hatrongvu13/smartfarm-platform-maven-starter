package com.htv.smartfarm.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SmartFarmSecurityPropertiesTest {

    @Test
    void shouldAcceptHttpsConfiguration() {
        var jwt = new SmartFarmSecurityProperties.Jwt(
                URI.create("https://identity.smartfarm.vn"),
                URI.create("https://identity.smartfarm.vn/oauth2/jwks"),
                Set.of("smartfarm-gateway"),
                false
        );

        var properties = new SmartFarmSecurityProperties(
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
        var jwt = new SmartFarmSecurityProperties.Jwt(
                URI.create("http://localhost:8092"),
                URI.create("http://localhost:8092/.well-known/jwks.json"),
                Set.of("smartfarm-gateway"),
                true
        );

        assertThat(jwt.allowLocalHttp()).isTrue();
    }

    @Test
    void shouldAcceptIpv6Loopback() {
        var jwt = new SmartFarmSecurityProperties.Jwt(
                URI.create("http://[::1]:8092"),
                URI.create("http://[::1]:8092/.well-known/jwks.json"),
                Set.of("smartfarm-gateway"),
                true
        );

        assertThat(jwt.issuer().getHost()).contains("::1");
    }

    @Test
    void shouldRejectRemoteHttp() {
        assertThatThrownBy(() ->
                new SmartFarmSecurityProperties.Jwt(
                        URI.create("http://identity.internal:8092"),
                        URI.create(
                                "http://identity.internal:8092/jwks.json"
                        ),
                        Set.of("smartfarm-gateway"),
                        true
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDifferentLoopbackPorts() {
        assertThatThrownBy(() ->
                new SmartFarmSecurityProperties.Jwt(
                        URI.create("http://localhost:8092"),
                        URI.create("http://localhost:9092/jwks.json"),
                        Set.of("smartfarm-gateway"),
                        true
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectEmptyAudienceSet() {
        assertThatThrownBy(() ->
                new SmartFarmSecurityProperties.Jwt(
                        URI.create("https://identity.smartfarm.vn"),
                        URI.create(
                                "https://identity.smartfarm.vn/jwks.json"
                        ),
                        Set.of(),
                        false
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidCorrelationHeader() {
        assertThatThrownBy(() ->
                new SmartFarmSecurityProperties.Correlation(
                        "Invalid Header",
                        128,
                        true
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveGrpcDeadline() {
        assertThatThrownBy(() ->
                new SmartFarmSecurityProperties.Grpc(
                        true,
                        true,
                        true,
                        Duration.ZERO
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
