package com.htv.farmverse.identity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tài liệu OpenAPI của REST quản trị (/me, /users, /roles, /permissions, /tenants).
 * Các endpoint OAuth2/OIDC chuẩn (/oauth2/**, /userinfo) do Spring Authorization Server cung cấp,
 * có mô tả ở {@code /.well-known/openid-configuration}, không nằm trong tài liệu này.
 *
 * <p>Bật bằng {@code springdoc.api-docs.enabled} và {@code springdoc.swagger-ui.enabled}: dev bật, prod tắt.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    static final String BEARER = "bearer";
    static final String PROBLEM = "Problem";

    @Bean
    OpenAPI identityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FarmVerse Identity API")
                        .version("v1")
                        .description("REST quản trị tài khoản, role, permission, tenant. "
                                + "Cần access token do chính identity-service cấp (aud chứa identity-service). "
                                + "Mọi lỗi trả RFC 9457 ProblemDetail kèm field `code` dạng SF-<MIEN>-<SO>."))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSchemas(PROBLEM, problemSchema()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** Thêm các phản hồi lỗi chung (400, 401, 403) vào mọi thao tác, nếu thao tác chưa tự khai. */
    @Bean
    OperationCustomizer problemResponses() {
        return (operation, handlerMethod) -> {
            addIfAbsent(operation, "400", "Dữ liệu không hợp lệ (SF-VALIDATION-001) hoặc request sai dạng");
            addIfAbsent(operation, "401", "Thiếu hoặc sai token (SF-AUTH-001)");
            addIfAbsent(operation, "403", "Không đủ quyền (SF-AUTH-002)");
            return operation;
        };
    }

    private static void addIfAbsent(io.swagger.v3.oas.models.Operation operation, String status, String description) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        if (operation.getResponses().get(status) != null) {
            return;
        }
        Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + PROBLEM);
        operation.getResponses().addApiResponse(status, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/problem+json", new MediaType().schema(ref))));
    }

    private static Schema<?> problemSchema() {
        ObjectSchema error = new ObjectSchema();
        error.addProperty("field", new StringSchema());
        error.addProperty("message", new StringSchema());

        ObjectSchema problem = new ObjectSchema();
        problem.description("RFC 9457 ProblemDetail, thêm `code` và (khi validation) `errors`");
        problem.addProperty("type", new StringSchema().example("urn:farmverse:problem:SF-VALIDATION-001"));
        problem.addProperty("title", new StringSchema());
        problem.addProperty("status", new IntegerSchema());
        problem.addProperty("detail", new StringSchema());
        problem.addProperty("instance", new StringSchema());
        problem.addProperty("code", new StringSchema().example("SF-VALIDATION-001"));
        problem.addProperty("errors", new ArraySchema().items(error));
        return problem;
    }
}
