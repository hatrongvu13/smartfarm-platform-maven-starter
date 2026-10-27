package com.htv.farmverse.farm.config;

import java.util.Set;

import com.htv.farmverse.service.mtls.MtlsClients;
import com.htv.farmverse.service.mtls.ServiceIdentityFilter;
import com.htv.farmverse.service.security.FarmVerseClaims;
import com.htv.farmverse.service.security.FarmVerseJwtValidators;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Chỉ bật với profile {@code mtls} (dev, prod). */
@Profile("mtls")
@Configuration(proxyBeanMethods = false)
public class MtlsConfiguration {

    /** Kiểm danh tính service gọi tới (SAN spiffe) trước mọi filter khác. */
    @Bean
    FilterRegistrationBean<ServiceIdentityFilter> serviceIdentityFilter(
            @Value("${farmverse.mtls.allowed-callers}") Set<String> allowed) {
        FilterRegistrationBean<ServiceIdentityFilter> registration =
                new FilterRegistrationBean<>(ServiceIdentityFilter.everyRequest(allowed));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** JWKS lấy từ identity qua mTLS (cert của farm). Thay decoder mặc định của Boot. */
    @Bean
    JwtDecoder jwtDecoder(SslBundles bundles,
                          @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
                          @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        return MtlsClients.jwtDecoder(jwkSetUri, bundles.getBundle("farmverse"),
                FarmVerseJwtValidators.issuerAndAudience(issuer, FarmVerseClaims.API_AUDIENCE));
    }
}
