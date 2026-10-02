package com.htv.smartfarm.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger metadata for the gateway — the single public ingress. Declares a
 * bearer-JWT scheme so the Swagger UI "Authorize" button lets a tester paste the
 * accessToken from /api/v1/auth/login and call protected endpoints interactively.
 *
 * <p>Swagger UI:  http://localhost:8080/swagger-ui.html
 * <p>OpenAPI JSON: http://localhost:8080/v3/api-docs
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI smartFarmOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartFarm Platform API (Gateway)")
                        .version("v1")
                        .description("Single public ingress for the SmartFarm platform. All external traffic enters "
                                + "here; internal services communicate over gRPC. Authenticate via POST /api/v1/auth/login, "
                                + "then click Authorize and paste the accessToken. Order write requests use Idempotency-Key; "
                                + "X-Correlation-Id is echoed and propagated downstream. Draft updates use optimistic versions, "
                                + "and order listing uses cursor pagination.")
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the accessToken returned by /api/v1/auth/login")))
                // Apply the scheme globally; public endpoints simply ignore it.
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
