package com.htv.farmverse.identity.audit;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Nhật ký bảo mật cho sự kiện của Spring Security: đăng nhập thành công, đăng nhập thất bại, truy cập bị từ chối.
 * Việc ghi và làm sạch dữ liệu nằm ở {@link SecurityAudit}; sự kiện nghiệp vụ (đổi role, khóa tài khoản...)
 * do chính service gọi {@link SecurityAudit}.
 */
@Component
public class SecurityAuditListener {

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        if (authentication instanceof AbstractOAuth2TokenAuthenticationToken<?>) {
            return; // mỗi request mang Bearer token cũng phát sự kiện này: bỏ để log không ngập
        }
        SecurityAudit.info("authentication.success", "actor", authentication.getName(),
                "factors", authentication.getAuthorities().size());
    }

    @EventListener
    void onFailure(AbstractAuthenticationFailureEvent event) {
        SecurityAudit.warn("authentication.failure", "actor", event.getAuthentication().getName(),
                "reason", event.getException().getClass().getSimpleName());
    }

    @EventListener
    void onDenied(AuthorizationDeniedEvent<?> event) {
        Authentication authentication = event.getAuthentication().get();
        SecurityAudit.warn("authorization.denied", "actor", authentication == null ? null : authentication.getName());
    }
}
