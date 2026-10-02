package com.htv.smartfarm.security.mqtt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Signs and verifies {@link MqttSecurityEnvelope} frames with HMAC-SHA256.
 *
 * <p>Behaviour is governed by {@link MqttSecurityProperties}; all toggles default
 * OFF, so a service that wires this in but leaves config untouched behaves
 * exactly as before:
 *
 * <ul>
 *   <li>{@code sign(topic, payload)} returns a signed frame when
 *       {@code sign-enabled=true}, otherwise returns the payload UNCHANGED.</li>
 *   <li>{@code verify(topic, frameOrPayload)} returns the inner payload. When
 *       {@code verify-enabled=false} it accepts anything (signed frames are
 *       unwrapped, legacy payloads passed through). When {@code verify-enabled=true}
 *       it REQUIRES a valid signed frame and rejects everything else.</li>
 * </ul>
 *
 * <p>The signature binds the topic too (HMAC over {@code topic bytes || envelope
 * signable region}), so a frame signed for one topic cannot be replayed on
 * another. Comparison is constant-time.
 */
public class MqttSecurityVerifier {

    private static final Logger log = LoggerFactory.getLogger(MqttSecurityVerifier.class);
    private static final String HMAC_ALG = "HmacSHA256";

    private final MqttSecurityProperties props;

    public MqttSecurityVerifier(MqttSecurityProperties props) {
        this.props = props == null ? MqttSecurityProperties.disabled() : props;
    }

    /** Outcome of a verify call: either an accepted payload or a rejection reason. */
    public static final class Result {
        private final boolean accepted;
        private final byte[] payload;
        private final String reason;

        private Result(boolean accepted, byte[] payload, String reason) {
            this.accepted = accepted;
            this.payload = payload;
            this.reason = reason;
        }

        public boolean accepted() {
            return accepted;
        }

        public byte[] payload() {
            return payload == null ? new byte[0] : payload.clone();
        }

        public String reason() {
            return reason;
        }

        static Result accept(byte[] payload) {
            return new Result(true, payload, null);
        }

        static Result reject(String reason) {
            return new Result(false, null, reason);
        }
    }

    /**
     * Wrap {@code payload} in a signed frame for {@code topic}. When signing is
     * disabled the payload is returned unchanged (no behaviour change).
     */
    public byte[] sign(String topic, byte[] payload) {
        if (!props.signEnabled()) {
            return payload;
        }
        if (props.hmacSecret().isBlank()) {
            throw new IllegalStateException(
                    "smartfarm.security.mqtt.sign-enabled=true but hmac-secret is blank");
        }
        MqttSecurityEnvelope unsigned = new MqttSecurityEnvelope(
                MqttSecurityEnvelope.VERSION_1,
                MqttSecurityEnvelope.ALG_HMAC_SHA256,
                props.keyId(),
                producerFromTopic(topic),
                System.currentTimeMillis(),
                payload,
                new byte[0]);
        byte[] signature = hmac(topic, unsigned.signableRegion());
        MqttSecurityEnvelope signed = new MqttSecurityEnvelope(
                unsigned.version(),
                unsigned.algId(),
                unsigned.keyId(),
                unsigned.producer(),
                unsigned.issuedAtEpochMillis(),
                payload,
                signature);
        return signed.toFrame();
    }

    /**
     * Verify {@code frameOrPayload} received on {@code topic} and return the inner
     * payload. Never throws — a structural or signature problem is a rejection.
     */
    public Result verify(String topic, byte[] frameOrPayload) {
        boolean signed = MqttSecurityEnvelope.looksSigned(frameOrPayload);

        if (!props.verifyEnabled()) {
            // Permissive mode: unwrap a signed frame if present, else pass through.
            if (signed) {
                try {
                    return Result.accept(MqttSecurityEnvelope.parse(frameOrPayload).payload());
                } catch (IllegalArgumentException e) {
                    // Looked signed but was malformed; in permissive mode keep raw bytes.
                    return Result.accept(frameOrPayload);
                }
            }
            return Result.accept(frameOrPayload);
        }

        // Enforcing mode: a valid signed frame is mandatory.
        if (!signed) {
            return Result.reject("unsigned payload rejected (verify-enabled=true)");
        }
        if (props.hmacSecret().isBlank()) {
            return Result.reject("verify-enabled=true but hmac-secret is blank");
        }
        final MqttSecurityEnvelope env;
        try {
            env = MqttSecurityEnvelope.parse(frameOrPayload);
        } catch (IllegalArgumentException e) {
            return Result.reject("malformed envelope: " + e.getMessage());
        }
        if (env.algId() != MqttSecurityEnvelope.ALG_HMAC_SHA256) {
            return Result.reject("unsupported alg id " + env.algId());
        }
        byte[] expected = hmac(topic, env.signableRegion());
        if (!MessageDigest.isEqual(expected, env.signature())) {
            return Result.reject("signature mismatch");
        }
        if (props.maxClockSkewMillis() > 0) {
            long age = Math.abs(System.currentTimeMillis() - env.issuedAtEpochMillis());
            if (age > props.maxClockSkewMillis()) {
                return Result.reject("envelope too old/future (age=" + age + "ms)");
            }
        }
        return Result.accept(env.payload());
    }

    private byte[] hmac(String topic, byte[] signableRegion) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(
                    props.hmacSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            mac.update((topic == null ? "" : topic).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0); // domain separator between topic and body
            mac.update(signableRegion);
            return mac.doFinal();
        } catch (Exception e) {
            // Only thrown on JVM misconfiguration (algorithm/key); surface loudly.
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private static String producerFromTopic(String topic) {
        // Best-effort audit tag; topic shape is smartfarm/{tenant}/{farm}/domain/{event}/{id}
        if (topic == null) {
            return "";
        }
        return topic;
    }
}
