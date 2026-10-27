package com.htv.farmverse.identity.login;

import java.io.IOException;

import com.htv.farmverse.identity.mfa.TotpService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Luật MFA có điều kiện trước endpoint {@code /oauth2/authorize}: tài khoản <b>đã bật TOTP</b> mà phiên chưa có
 * {@code FACTOR_TOTP} thì không được cấp code — lưu request gốc rồi chuyển sang {@code /login/totp}.
 * Tài khoản chưa bật TOTP chỉ cần mật khẩu.
 *
 * <p>Phải là filter đứng trước filter authorize của SAS: SAS cấp code ngay khi principal đã xác thực,
 * không qua {@code authorizeHttpRequests}.
 */
public class TotpEnforcementFilter extends OncePerRequestFilter {

    private final TotpService totp;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public TotpEnforcementFilter(TotpService totp) {
        this.totp = totp;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(request.getContextPath() + "/oauth2/authorize").equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken)
                && !TotpLogin.hasFactor(current)) {
            String userId = totp.userIdOfMembership(current.getName());
            if (userId != null && totp.enabled(userId)) {
                requestCache.saveRequest(request, response);
                response.sendRedirect(request.getContextPath() + TotpLogin.PAGE);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
