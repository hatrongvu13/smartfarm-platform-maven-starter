package com.htv.farmverse.farm.adapter.out.jpa;

import java.util.Map;

import com.htv.farmverse.service.security.FarmVerseClaims;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Hibernate hỏi "tenant hiện tại là ai?" mỗi khi mở session: lấy claim {@code tenant_id} của access token.
 * Không có token (ví dụ lúc khởi động) → {@value #NO_TENANT}: truy vấn không trả dòng nào.
 */
@Component
class TenantFromTokenResolver implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    static final String NO_TENANT = "__none__";

    @Override
    public String resolveCurrentTenantIdentifier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String tenant = jwt.getToken().getClaimAsString(FarmVerseClaims.TENANT_ID);
            if (tenant != null && !tenant.isBlank()) {
                return tenant;
            }
        }
        return NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
