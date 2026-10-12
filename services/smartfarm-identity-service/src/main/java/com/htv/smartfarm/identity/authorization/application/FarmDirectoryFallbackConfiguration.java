package com.htv.smartfarm.identity.authorization.application;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class FarmDirectoryFallbackConfiguration {

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
