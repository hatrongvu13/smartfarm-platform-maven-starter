package com.htv.smartfarm.security.autoconfigure;

import com.htv.smartfarm.security.authorization.AuthorizationVersionWatermarkStore;
import com.htv.smartfarm.security.authorization.AuthorizationInvalidationObserver;
import com.htv.smartfarm.security.authorization.InMemoryAuthorizationVersionWatermarkStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import com.htv.smartfarm.security.authorization.AuthorizationInvalidationMqttConsumer;
import com.htv.smartfarm.security.authorization.AuthorizationInvalidationMqttProperties;
import com.htv.smartfarm.security.mqtt.MqttSecurityVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AuthorizationInvalidationMqttProperties.class)
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

    @Bean
    @ConditionalOnClass(name = "org.eclipse.paho.client.mqttv3.MqttClient")
    @ConditionalOnProperty(prefix = "smartfarm.security.authorization-invalidation.mqtt",
            name = "enabled", havingValue = "true")
    AuthorizationInvalidationMqttConsumer authorizationInvalidationMqttConsumer(
            AuthorizationInvalidationMqttProperties properties,
            AuthorizationInvalidationObserver observer,
            MqttSecurityVerifier security) {
        return new AuthorizationInvalidationMqttConsumer(properties, observer, security);
    }
}
