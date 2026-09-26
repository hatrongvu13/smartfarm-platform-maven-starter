package com.htv.smartfarm.gateway.config;

import com.htv.smartfarm.security.config.SecurityProperties;
import com.htv.smartfarm.security.web.ReactiveResourceSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;

/**
 * Fail-fast configuration. No permitAll dev profile and no duplicate SecurityWebFilterChain.
 */
@Configuration(proxyBeanMethods = false)
@EnableReactiveMethodSecurity
@Import(ReactiveResourceSecurity.class)
public class GatewaySecurityConfiguration {
    @Bean
    SecurityProperties smartFarmSecurityProperties(Environment environment) {
        boolean devProfile = environment.acceptsProfiles(
                Profiles.of("dev & !prod")
        );

        boolean allowLocalHttp = devProfile && environment.getProperty("smartfarm.security.allow-local-http", Boolean.class, false);
        return new SecurityProperties(
                environment.getRequiredProperty("smartfarm.security.issuer"),
                environment.getRequiredProperty("smartfarm.security.jwk-set-uri"),
                environment.getRequiredProperty("smartfarm.security.audience"),
                allowLocalHttp);
    }
}
