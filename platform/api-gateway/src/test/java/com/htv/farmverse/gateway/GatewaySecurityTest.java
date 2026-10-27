package com.htv.farmverse.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Không cần identity hay Redis đang chạy: client OIDC khai tĩnh (không discovery),
 * các request ở đây không mở session.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "farmverse.gateway.oidc.client-secret=test-secret",
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
