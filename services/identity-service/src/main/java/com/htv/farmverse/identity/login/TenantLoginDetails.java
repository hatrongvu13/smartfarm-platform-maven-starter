package com.htv.farmverse.identity.login;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Thông tin phụ của form login: mã tenant người dùng chọn (field {@code tenant}).
 * Chỉ sống trong lúc xác thực; kết quả đăng nhập lưu lại {@link WebAuthenticationDetails} chuẩn
 * để Spring Authorization Server tuần tự hóa được.
 */
public class TenantLoginDetails extends WebAuthenticationDetails {

    public static final String TENANT_PARAMETER = "tenant";

    private final String tenantCode;

    public TenantLoginDetails(HttpServletRequest request) {
        super(request);
        String value = request.getParameter(TENANT_PARAMETER);
        this.tenantCode = value == null || value.isBlank() ? null : value.trim();
    }

    public String tenantCode() {
        return tenantCode;
    }

    public WebAuthenticationDetails plain() {
        return new WebAuthenticationDetails(getRemoteAddress(), getSessionId());
    }

    public static AuthenticationDetailsSource<HttpServletRequest, TenantLoginDetails> source() {
        return TenantLoginDetails::new;
    }
}
