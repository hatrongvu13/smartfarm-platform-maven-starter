package com.htv.smartfarm.simulator.verify;

import java.nio.charset.StandardCharsets;

import com.htv.smartfarm.security.mqtt.MqttSecurityProperties;
import com.htv.smartfarm.security.mqtt.MqttSecurityVerifier;

/**
 * Negative controls for ISSUE-02 enforcement (verify-enabled=true):
 * a good signed frame is ACCEPTED, while unsigned / tampered / wrong-secret
 * frames are REJECTED. Proves the verifier enforces, not just unwraps.
 */
public final class HmacEnforcementCheck {
    public static void main(String[] args) {
        String secret = "dev-mqtt-hmac-secret-shared-across-all-services";
        String topic = "smartfarm/farm-demo/_global/domain/failed/v1";
        byte[] body = "hello-domain-event".getBytes(StandardCharsets.UTF_8);

        MqttSecurityVerifier signer = new MqttSecurityVerifier(
                new MqttSecurityProperties(true, false, secret, "dev-key-1", 0L));
        MqttSecurityVerifier enforcer = new MqttSecurityVerifier(
                new MqttSecurityProperties(false, true, secret, "dev-key-1", 0L));
        MqttSecurityVerifier wrongSecret = new MqttSecurityVerifier(
                new MqttSecurityProperties(false, true, "a-different-secret", "dev-key-1", 0L));

        byte[] signed = signer.sign(topic, body);

        // 1. good frame -> ACCEPTED
        print("good signed frame", enforcer.verify(topic, signed).accepted(), true);

        // 2. unsigned raw payload -> REJECTED
        print("unsigned payload", enforcer.verify(topic, body).accepted(), false);

        // 3. tampered frame (flip one byte in the signed region) -> REJECTED
        byte[] tampered = signed.clone();
        tampered[tampered.length - 40] ^= 0x01;
        print("tampered frame", enforcer.verify(topic, tampered).accepted(), false);

        // 4. wrong topic (signature is topic-bound) -> REJECTED
        print("replayed on other topic",
                enforcer.verify("smartfarm/farm-demo/_global/domain/other/v1", signed).accepted(), false);

        // 5. wrong secret -> REJECTED
        print("verifier with wrong secret", wrongSecret.verify(topic, signed).accepted(), false);

        System.exit(0);
    }

    private static void print(String name, boolean got, boolean want) {
        String verdict = (got == want) ? "PASS" : "FAIL";
        System.out.printf("[%s] %-28s accepted=%s (expected=%s)%n", verdict, name, got, want);
    }
}
