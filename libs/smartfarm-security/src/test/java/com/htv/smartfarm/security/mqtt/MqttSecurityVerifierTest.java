package com.htv.smartfarm.security.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MqttSecurityVerifierTest {

    private static final String SECRET = "test-hmac-secret-0123456789";
    private static final String TOPIC = "smartfarm/t1/f1/domain/order-changed/42";
    private static final byte[] PAYLOAD = "protobuf-bytes".getBytes(StandardCharsets.UTF_8);

    private MqttSecurityVerifier verifier(boolean sign, boolean verify, long skew) {
        return new MqttSecurityVerifier(
                new MqttSecurityProperties(sign, verify, SECRET, "k1", skew));
    }

    @Test
    void signThenVerify_roundTrips() {
        MqttSecurityVerifier v = verifier(true, true, 0);
        byte[] frame = v.sign(TOPIC, PAYLOAD);
        assertThat(MqttSecurityEnvelope.looksSigned(frame)).isTrue();

        MqttSecurityVerifier.Result r = v.verify(TOPIC, frame);
        assertThat(r.accepted()).isTrue();
        assertThat(r.payload()).isEqualTo(PAYLOAD);
    }

    @Test
    void tamperedPayload_isRejected() {
        MqttSecurityVerifier v = verifier(true, true, 0);
        byte[] frame = v.sign(TOPIC, PAYLOAD);
        frame[frame.length - 10] ^= 0x7F; // flip a bit somewhere in the body/sig

        MqttSecurityVerifier.Result r = v.verify(TOPIC, frame);
        assertThat(r.accepted()).isFalse();
        assertThat(r.reason()).isNotBlank();
    }

    @Test
    void frameSignedForOneTopic_rejectedOnAnother() {
        MqttSecurityVerifier v = verifier(true, true, 0);
        byte[] frame = v.sign(TOPIC, PAYLOAD);

        MqttSecurityVerifier.Result r = v.verify(TOPIC + "-other", frame);
        assertThat(r.accepted()).isFalse();
        assertThat(r.reason()).isEqualTo("signature mismatch");
    }

    @Test
    void enforcingMode_rejectsUnsignedLegacyPayload() {
        MqttSecurityVerifier v = verifier(false, true, 0);
        MqttSecurityVerifier.Result r = v.verify(TOPIC, PAYLOAD);
        assertThat(r.accepted()).isFalse();
        assertThat(r.reason()).contains("unsigned payload rejected");
    }

    @Test
    void permissiveMode_passesThroughLegacyPayloadUnchanged() {
        MqttSecurityVerifier v = verifier(false, false, 0);
        assertThat(v.sign(TOPIC, PAYLOAD)).isEqualTo(PAYLOAD); // sign disabled => unchanged
        MqttSecurityVerifier.Result r = v.verify(TOPIC, PAYLOAD);
        assertThat(r.accepted()).isTrue();
        assertThat(r.payload()).isEqualTo(PAYLOAD);
    }

    @Test
    void permissiveMode_unwrapsSignedFrame() {
        byte[] frame = verifier(true, false, 0).sign(TOPIC, PAYLOAD);
        MqttSecurityVerifier.Result r = verifier(false, false, 0).verify(TOPIC, frame);
        assertThat(r.accepted()).isTrue();
        assertThat(r.payload()).isEqualTo(PAYLOAD);
    }

    @Test
    void clockSkew_rejectsStaleEnvelope() throws Exception {
        MqttSecurityVerifier signer = verifier(true, false, 0);
        byte[] frame = signer.sign(TOPIC, PAYLOAD);
        Thread.sleep(5);
        MqttSecurityVerifier v = verifier(false, true, 1); // 1ms window
        MqttSecurityVerifier.Result r = v.verify(TOPIC, frame);
        assertThat(r.accepted()).isFalse();
        assertThat(r.reason()).contains("too old");
    }

    @Test
    void permissiveMode_rejectsMalformedSignedFrame() {
        byte[] malformed = new byte[]{'S', 'F', 'M', '1', 1};
        MqttSecurityVerifier.Result result = verifier(false, false, 0)
                .verify(TOPIC, malformed);
        assertThat(result.accepted()).isFalse();
        assertThat(result.reason()).isEqualTo("malformed signed envelope");
    }

    @Test
    void envelopeParserRejectsUnsupportedVersion() {
        byte[] frame = verifier(true, false, 0).sign(TOPIC, PAYLOAD);
        frame[MqttSecurityEnvelope.MAGIC.length] = 2;
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> MqttSecurityEnvelope.parse(frame))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported envelope version");
    }

    @Test
    void envelopeParserRejectsTrailingBytes() {
        byte[] frame = verifier(true, false, 0).sign(TOPIC, PAYLOAD);
        byte[] withTrailing = java.util.Arrays.copyOf(frame, frame.length + 1);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> MqttSecurityEnvelope.parse(withTrailing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trailing bytes");
    }

    @Test
    void envelopeFrame_parsesBackToSameFields() {
        MqttSecurityEnvelope env = new MqttSecurityEnvelope(
                MqttSecurityEnvelope.VERSION_1,
                MqttSecurityEnvelope.ALG_HMAC_SHA256,
                "k1", "producer-x", 1_700_000_000_000L, PAYLOAD, new byte[32]);
        MqttSecurityEnvelope parsed = MqttSecurityEnvelope.parse(env.toFrame());
        assertThat(parsed.keyId()).isEqualTo("k1");
        assertThat(parsed.producer()).isEqualTo("producer-x");
        assertThat(parsed.issuedAtEpochMillis()).isEqualTo(1_700_000_000_000L);
        assertThat(parsed.payload()).isEqualTo(PAYLOAD);
        assertThat(parsed.signature()).isEqualTo(new byte[32]);
    }
}
