package com.htv.farmverse.identity.messaging.command;

import com.htv.farmverse.security.mqtt.MqttSecurityProperties;

import org.springframework.stereotype.Component;

@Component
public class IdentityMqttCommandSecurityGuard {

    public IdentityMqttCommandSecurityGuard(
            IdentityMqttCommandProperties commands,
            MqttSecurityProperties security
    ) {
        if (!commands.enabled()) {
            return;
        }
        if (!security.verifyEnabled()) {
            throw new IllegalStateException(
                    "Identity MQTT commands require farmverse.security.mqtt.verify-enabled=true"
            );
        }
        if (security.hmacSecret().isBlank()) {
            throw new IllegalStateException(
                    "Identity MQTT commands require a non-blank farmverse.security.mqtt.hmac-secret"
            );
        }
    }
}
