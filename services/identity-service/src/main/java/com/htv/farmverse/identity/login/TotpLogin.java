package com.htv.farmverse.identity.login;

import java.util.List;

import com.htv.farmverse.identity.mfa.TotpService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Yếu tố thứ hai: {@code POST /login/totp} với field {@code code}.
 *
 * <p>Dựng trên cơ chế MFA của Spring Security 7: filter bật {@code setMfaEnabled(true)} nên khi xác thực thành công,
 * Spring <b>gộp</b> authority {@code FACTOR_TOTP} vào phiên đăng nhập đang có (đã có {@code FACTOR_PASSWORD}),
 * rồi quay về request bị chặn trước đó (ví dụ {@code /oauth2/authorize}).
 */
public final class TotpLogin {

    /** {@code FACTOR_TOTP} — factor tùy biến, cùng kiểu với FACTOR_PASSWORD / FACTOR_OTT của Spring. */
    public static final String FACTOR = "TOTP";
    public static final String AUTHORITY = FactorGrantedAuthority.withFactor(FACTOR).build().getAuthority();
    public static final String PAGE = "/login/totp";

    private TotpLogin() {
    }

    public static boolean hasFactor(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch(AUTHORITY::equals);
    }

    public static AbstractAuthenticationProcessingFilter filter(TotpService totp) {
        Filter filter = new Filter(new ProviderManager(new Provider(totp)));
        filter.setMfaEnabled(true);
        filter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
        filter.setAuthenticationSuccessHandler(new SavedRequestAwareAuthenticationSuccessHandler());
        filter.setAuthenticationFailureHandler(new SimpleUrlAuthenticationFailureHandler(PAGE + "?error"));
        return filter;
    }

    /** Chưa xác thực: principal là membership id đang đăng nhập, credentials là mã 6 số. */
    static final class Token extends AbstractAuthenticationToken {

        private final String membershipId;
        private final String code;

        Token(String membershipId, String code) {
            super(List.of());
            this.membershipId = membershipId;
            this.code = code;
        }

        @Override
        public Object getCredentials() {
            return code;
        }

        @Override
        public Object getPrincipal() {
            return membershipId;
        }
    }

    static final class Filter extends AbstractAuthenticationProcessingFilter {

        Filter(ProviderManager manager) {
            super(PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.POST, PAGE), manager);
        }

        @Override
        public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
                throws AuthenticationException {
            Authentication current = SecurityContextHolder.getContext().getAuthentication();
            if (current == null || !current.isAuthenticated()) {
                throw new BadCredentialsException("Password step required first");
            }
            return getAuthenticationManager().authenticate(new Token(current.getName(), request.getParameter("code")));
        }
    }

    /** Kiểm mã bằng {@link TotpService}; thành công trả về đúng principal cũ kèm FACTOR_TOTP. */
    static final class Provider implements AuthenticationProvider {

        private final TotpService totp;

        Provider(TotpService totp) {
            this.totp = totp;
        }

        @Override
        public Authentication authenticate(Authentication authentication) {
            String membershipId = authentication.getName();
            String userId = totp.userIdOfMembership(membershipId);
            if (userId == null || !totp.verify(userId, String.valueOf(authentication.getCredentials()))) {
                throw new BadCredentialsException("Invalid TOTP code");
            }
            GrantedAuthority factor = FactorGrantedAuthority.withFactor(FACTOR).build();
            User principal = (User) User.withUsername(membershipId).password("").authorities(List.of(factor)).build();
            return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(factor));
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return Token.class.isAssignableFrom(authentication);
        }
    }
}
