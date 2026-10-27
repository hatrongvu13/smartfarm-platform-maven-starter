package com.htv.farmverse.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Không cần identity hay Redis đang chạy: provider khai endpoint tĩnh (không discovery),
 * các request ở đây không mở session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.security.oauth2.client.registration.farmverse.client-secret=test-secret",
        "spring.security.oauth2.client.provider.farmverse.authorization-uri=http://localhost:8092/oauth2/authorize",
        "spring.security.oauth2.client.provider.farmverse.token-uri=http://localhost:8092/oauth2/token",
        "spring.security.oauth2.client.provider.farmverse.jwk-set-uri=http://localhost:8092/oauth2/jwks",
        "spring.security.oauth2.client.provider.farmverse.user-name-attribute=sub",
        "spring.data.redis.password=test",
        "spring.profiles.active=test"
})
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    @Autowired
    WebTestClient web;

    @Test
    void apiWithoutSessionIsUnauthorized() {
        web.get().uri("/api/farm/farms").exchange().expectStatus().isUnauthorized();
        web.get().uri("/bff/me").exchange().expectStatus().isUnauthorized();
    }

    @Autowired
    org.springframework.cloud.gateway.route.RouteLocator routes;

    @Test
    void routesAreDeclared() {
        var ids = routes.getRoutes().map(org.springframework.cloud.gateway.route.Route::getId).collectList().block();
        org.assertj.core.api.Assertions.assertThat(ids).containsExactly("identity-api", "farm-api", "web");
    }
}
