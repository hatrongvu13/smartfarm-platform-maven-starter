package com.htv.smartfarm.security.autoconfigure;

import com.htv.smartfarm.security.authorization.AuthorizationVersionWatermarkStore;
import com.htv.smartfarm.security.authorization.AuthorizationInvalidationObserver;
import com.htv.smartfarm.security.authorization.InMemoryAuthorizationVersionWatermarkStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class SmartFarmAuthorizationFreshnessAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuthorizationVersionWatermarkStore.class)
    AuthorizationVersionWatermarkStore authorizationVersionWatermarkStore() {
        return new InMemoryAuthorizationVersionWatermarkStore();
    }
    @Bean
    @ConditionalOnMissingBean(AuthorizationInvalidationObserver.class)
    AuthorizationInvalidationObserver authorizationInvalidationObserver(AuthorizationVersionWatermarkStore store) {
        return new AuthorizationInvalidationObserver(store);
    }
}
