package com.htv.smartfarm.identity.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceTokenSettingsWildcardTest {
    @Test
    void rejectsWildcardServiceScope() {
        var client = new ServiceTokenSettings.Client("encoded-secret",
                List.of("smartfarm-identity"), Map.of("smartfarm-identity", List.of("*")));
        assertThatThrownBy(() -> new ServiceTokenSettings(Duration.ofMinutes(5), Map.of("gateway", client)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("wildcard");
    }

    @Test
    void rejectsScopesForNonWhitelistedAudience() {
        var client = new ServiceTokenSettings.Client("encoded-secret",
                List.of("smartfarm-identity"), Map.of("smartfarm-order", List.of("orders:read")));
        assertThatThrownBy(() -> new ServiceTokenSettings(Duration.ofMinutes(5), Map.of("gateway", client)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-whitelisted");
    }
}
