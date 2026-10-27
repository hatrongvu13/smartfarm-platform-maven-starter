package com.htv.farmverse.identity.config;

import java.util.List;
import java.util.Set;

import com.htv.farmverse.service.mtls.ServiceIdentityFilter;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

/** Chỉ bật với profile {@code mtls} (dev, prod). */
@Profile("mtls")
@Configuration(proxyBeanMethods = false)
public class MtlsConfiguration {

    /** Đường chỉ service gọi (không bao giờ là trình duyệt). */
    static final List<String> BACK_CHANNEL = List.of(
            "/oauth2/token", "/oauth2/jwks", "/oauth2/introspect", "/oauth2/revoke", "/userinfo",
            "/me", "/users", "/roles", "/permissions", "/tenants");

    @Bean
    FilterRegistrationBean<ServiceIdentityFilter> serviceIdentityFilter(
            @Value("${farmverse.mtls.allowed-callers}") Set<String> allowed) {
        FilterRegistrationBean<ServiceIdentityFilter> registration =
                new FilterRegistrationBean<>(new ServiceIdentityFilter(allowed, MtlsConfiguration::isBackChannel));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    static boolean isBackChannel(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return BACK_CHANNEL.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
