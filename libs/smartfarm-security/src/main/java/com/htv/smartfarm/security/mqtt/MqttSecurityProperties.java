package com.htv.smartfarm.security.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for app-level MQTT message authentication (signed envelope).
 *
 * <p>Prefix {@code smartfarm.security.mqtt}. ALL toggles default to OFF so that
 * adding this component to a service changes NO runtime behaviour until an
 * operator opts in — the rollout is: deploy with secrets configured and
 * {@code sign-enabled=true} on every producer first, let signed traffic flow
 * while {@code verify-enabled=false}, then flip {@code verify-enabled=true} on
 * consumers once all producers are known to sign.
 *
 * <p>This is defence-in-depth at the APPLICATION layer. It is independent of,
 * and complementary to, broker-level ACL + mTLS (see
 * {@code docs/security/mqtt-acl-mtls.md}) which this project does not yet enable.
 */
@ConfigurationProperties(prefix = "smartfarm.security.mqtt")
public record MqttSecurityProperties(
        boolean signEnabled,
        boolean verifyEnabled,
        String hmacSecret,
        String keyId,
        long maxClockSkewMillis
) {

    public MqttSecurityProperties {
        keyId = (keyId == null || keyId.isBlank()) ? "default" : keyId.trim();
        hmacSecret = hmacSecret == null ? "" : hmacSecret;
        // 0 disables the freshness check; a positive value bounds how old a
        // signed frame may be (replay-window mitigation). Default 0 = off.
        if (maxClockSkewMillis < 0) {
            maxClockSkewMillis = 0;
        }
    }

    public static MqttSecurityProperties disabled() {
        return new MqttSecurityProperties(false, false, "", "default", 0);
    }
}
