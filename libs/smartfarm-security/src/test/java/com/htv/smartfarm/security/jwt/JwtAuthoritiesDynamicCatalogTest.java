package com.htv.smartfarm.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtAuthoritiesDynamicCatalogTest {
    @Test void acceptsAWellFormedPermissionWithoutACompiledCatalogEntry() {
        assertThat(JwtAuthorities.toAuthority("sensor:calibration:approve"))
                .isEqualTo("SCOPE_sensor:calibration:approve");
    }

    @Test void rejectsMalformedPermissionCodes() {
        assertThat(JwtAuthorities.toAuthority("not registered with spaces"))
                .isNull();
        assertThat(JwtAuthorities.toAuthority("singlepart"))
                .isNull();
    }
}
