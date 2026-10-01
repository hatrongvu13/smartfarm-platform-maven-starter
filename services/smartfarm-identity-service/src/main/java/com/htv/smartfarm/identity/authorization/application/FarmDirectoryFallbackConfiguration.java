package com.htv.smartfarm.identity.authorization.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Temporary fail-closed Farm Directory adapter.
 *
 * Remove this configuration when a real FarmDirectoryPort implementation is
 * connected to FarmDirectoryService. Returning false prevents granting access
 * to a farm that Identity cannot verify.
 */
@Configuration(proxyBeanMethods = false)
public class FarmDirectoryFallbackConfiguration {

    private static final Logger log = LoggerFactory.getLogger(
            FarmDirectoryFallbackConfiguration.class
    );

    @Bean
    public FarmDirectoryPort farmDirectoryPort() {
        log.warn(
                "Using fail-closed FarmDirectoryPort fallback. "
                        + "New farm access grants will be rejected until the "
                        + "Farm Directory adapter is configured."
        );

        return new FarmDirectoryPort() {
            @Override
            public boolean existsInTenant(
                    String tenantId,
                    String farmId
            ) {
                return false;
            }
        };
    }
}
