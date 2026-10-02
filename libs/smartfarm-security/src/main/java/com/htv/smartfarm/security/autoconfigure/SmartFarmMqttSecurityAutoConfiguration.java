package com.htv.smartfarm.security.autoconfigure;

import com.htv.smartfarm.security.mqtt.MqttSecurityProperties;
import com.htv.smartfarm.security.mqtt.MqttSecurityVerifier;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Provides a shared {@link MqttSecurityVerifier} bound to
 * {@link MqttSecurityProperties}. All toggles default OFF, so merely having this
 * bean on the classpath changes no runtime behaviour — a producer's
 * {@code sign()} returns the payload unchanged and a consumer's {@code verify()}
 * accepts everything until an operator opts in via {@code smartfarm.security.mqtt.*}.
 */
@AutoConfiguration
@EnableConfigurationProperties(MqttSecurityProperties.class)
public class SmartFarmMqttSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MqttSecurityVerifier mqttSecurityVerifier(MqttSecurityProperties props) {
        return new MqttSecurityVerifier(props);
    }
}
