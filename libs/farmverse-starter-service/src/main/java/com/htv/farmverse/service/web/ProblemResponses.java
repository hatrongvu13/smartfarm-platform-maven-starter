package com.htv.farmverse.service.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Lỗi bảo mật (401, 403, 429) ở tầng filter trả RFC 9457 ProblemDetail, cùng dạng với {@link ApiProblem}.
 * Các lỗi này xảy ra trước Spring MVC nên {@code @RestControllerAdvice} không bắt được.
 *
 * <p>Chuỗi trong body là hằng số của lớp này, không chứa dữ liệu từ request, nên ghép JSON trực tiếp là an toàn.
 */
public final class ProblemResponses {

    public static final String UNAUTHENTICATED = "SF-AUTH-001";
    public static final String FORBIDDEN = "SF-AUTH-002";
    public static final String TOO_MANY_REQUESTS = "SF-AUTH-003";

    private ProblemResponses() {
    }

    /** 401: giữ header {@code WWW-Authenticate: Bearer} chuẩn của Spring, thêm body ProblemDetail. */
    public static AuthenticationEntryPoint unauthorized() {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            bearer.commence(request, response, exception);
            write(response, 401, "Unauthorized", UNAUTHENTICATED, "Cần đăng nhập hoặc token không hợp lệ");
        };
    }

    /** 403: đã đăng nhập nhưng thiếu quyền. */
    public static AccessDeniedHandler forbidden() {
        return (request, response, exception) ->
                write(response, 403, "Forbidden", FORBIDDEN, "Không đủ quyền thực hiện thao tác này");
    }

    public static void write(HttpServletResponse response, int status, String title, String code, String detail)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String body = "{\"type\":\"urn:farmverse:problem:" + code + "\",\"title\":\"" + title
                + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}";
        response.getWriter().write(body);
    }
}
