package com.htv.smartfarm.identity.config;

import jakarta.validation.constraints.AssertTrue;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "smartfarm.identity.superadmin-bootstrap")
public record SuperAdminBootstrapProperties(String token) {

    @AssertTrue(message = "super-admin bootstrap token must contain at least 32 characters")
    public boolean isTokenStrongEnough() {
        return token == null || token.isBlank() || token.length() >= 32;
    }
}
