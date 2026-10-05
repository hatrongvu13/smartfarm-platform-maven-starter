package com.htv.smartfarm.security.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Signed envelope for an MQTT integration-event payload.
 *
 * <p>Wire format (self-describing frame) — allows a verifier to tell a signed
 * frame from a legacy/unsigned payload without any out-of-band flag:
 *
 * <pre>
 *   MAGIC(4) = 'S','F','M','1'
 *   version(1)                     // envelope schema version, currently 1
 *   algId(1)                       // 1 = HMAC-SHA256
 *   keyIdLen(1)  keyId(keyIdLen)   // which signing key produced this frame
 *   producerLen(1) producer(..)    // logical producer id (service name), audit only
 *   issuedAtEpochMillis(8)
 *   payloadLen(4)  payload(payloadLen)
 *   sigLen(2)      signature(sigLen)   // HMAC over [MAGIC..payload] (everything before sigLen)
 * </pre>
 *
 * <p>This is a pure value/codec type: it holds the decoded fields and knows how
 * to serialize the signable region. It performs NO crypto itself — signing and
 * verification live in {@link MqttSecurityVerifier}, which owns the key material.
 *
 * <p>Backward compatibility: a byte[] that does not start with {@link #MAGIC} is
 * treated as a legacy unsigned payload by the verifier (passed through when
 * verification is disabled, rejected when it is enabled and the frame is required).
 */
public final class MqttSecurityEnvelope {

    public static final byte[] MAGIC = {'S', 'F', 'M', '1'};
    public static final byte VERSION_1 = 1;
    public static final byte ALG_HMAC_SHA256 = 1;

    private final byte version;
    private final byte algId;
    private final String keyId;
    private final String producer;
    private final long issuedAtEpochMillis;
    private final byte[] payload;
    private final byte[] signature;

    public MqttSecurityEnvelope(byte version,
                                byte algId,
                                String keyId,
                                String producer,
                                long issuedAtEpochMillis,
                                byte[] payload,
                                byte[] signature) {
        this.version = version;
        this.algId = algId;
        this.keyId = keyId == null ? "" : keyId;
        this.producer = producer == null ? "" : producer;
        this.issuedAtEpochMillis = issuedAtEpochMillis;
        this.payload = payload == null ? new byte[0] : payload.clone();
        this.signature = signature == null ? new byte[0] : signature.clone();
    }

    public byte version() {
        return version;
    }

    public byte algId() {
        return algId;
    }

    public String keyId() {
        return keyId;
    }

    public String producer() {
        return producer;
    }

    public long issuedAtEpochMillis() {
        return issuedAtEpochMillis;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public byte[] signature() {
        return signature.clone();
    }

    /** True if {@code frame} begins with the SmartFarm signed-envelope magic. */
    public static boolean looksSigned(byte[] frame) {
        if (frame == null || frame.length < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (frame[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * The bytes covered by the signature: the whole frame up to (but not
     * including) the sigLen field. Used by {@link MqttSecurityVerifier} both when
     * signing (compute HMAC over this) and when verifying (recompute + compare).
     */
    byte[] signableRegion() {
        byte[] keyIdBytes = keyId.getBytes(StandardCharsets.UTF_8);
        byte[] producerBytes = producer.getBytes(StandardCharsets.UTF_8);
        int len = MAGIC.length
                + 1 /* version */
                + 1 /* algId */
                + 1 + keyIdBytes.length
                + 1 + producerBytes.length
                + 8 /* issuedAt */
                + 4 + payload.length;
        byte[] out = new byte[len];
        int p = 0;
        System.arraycopy(MAGIC, 0, out, p, MAGIC.length);
        p += MAGIC.length;
        out[p++] = version;
        out[p++] = algId;
        out[p++] = (byte) keyIdBytes.length;
        System.arraycopy(keyIdBytes, 0, out, p, keyIdBytes.length);
        p += keyIdBytes.length;
        out[p++] = (byte) producerBytes.length;
        System.arraycopy(producerBytes, 0, out, p, producerBytes.length);
        p += producerBytes.length;
        p = writeLong(out, p, issuedAtEpochMillis);
        p = writeInt(out, p, payload.length);
        System.arraycopy(payload, 0, out, p, payload.length);
        return out;
    }

    /** Full serialized frame = signable region + sigLen(2) + signature. */
    public byte[] toFrame() {
        byte[] region = signableRegion();
        byte[] out = new byte[region.length + 2 + signature.length];
        System.arraycopy(region, 0, out, 0, region.length);
        int p = region.length;
        out[p++] = (byte) ((signature.length >>> 8) & 0xFF);
        out[p++] = (byte) (signature.length & 0xFF);
        System.arraycopy(signature, 0, out, p, signature.length);
        return out;
    }

    /**
     * Decode a frame produced by {@link #toFrame()}. Throws
     * {@link IllegalArgumentException} on any structural problem — the verifier
     * turns that into a rejection, never a thrown exception to the caller.
     */
    public static MqttSecurityEnvelope parse(byte[] frame) {
        if (!looksSigned(frame)) {
            throw new IllegalArgumentException("not a SmartFarm signed envelope");
        }
        int p = MAGIC.length;
        requireRemaining(frame, p, 2, "version and algorithm");
        byte version = frame[p++];
        byte algId = frame[p++];
        if (version != VERSION_1) {
            throw new IllegalArgumentException("unsupported envelope version " + version);
        }
        if (algId != ALG_HMAC_SHA256) {
            throw new IllegalArgumentException("unsupported envelope algorithm " + algId);
        }

        requireRemaining(frame, p, 1, "key id length");
        int keyIdLen = frame[p++] & 0xFF;
        requireRemaining(frame, p, keyIdLen, "key id");
        String keyId = new String(frame, p, keyIdLen, StandardCharsets.UTF_8);
        p += keyIdLen;

        requireRemaining(frame, p, 1, "producer length");
        int producerLen = frame[p++] & 0xFF;
        requireRemaining(frame, p, producerLen, "producer");
        String producer = new String(frame, p, producerLen, StandardCharsets.UTF_8);
        p += producerLen;

        requireRemaining(frame, p, 12, "timestamp and payload length");
        long issuedAt = readLong(frame, p);
        p += 8;
        int payloadLen = readInt(frame, p);
        p += 4;
        if (payloadLen < 0) {
            throw new IllegalArgumentException("invalid payload length");
        }
        requireRemaining(frame, p, payloadLen, "payload");
        byte[] payload = Arrays.copyOfRange(frame, p, p + payloadLen);
        p += payloadLen;

        requireRemaining(frame, p, 2, "signature length");
        int sigLen = ((frame[p++] & 0xFF) << 8) | (frame[p++] & 0xFF);
        if (sigLen != 32) {
            throw new IllegalArgumentException("invalid signature length " + sigLen);
        }
        requireRemaining(frame, p, sigLen, "signature");
        if (p + sigLen != frame.length) {
            throw new IllegalArgumentException("trailing bytes in envelope frame");
        }
        byte[] signature = Arrays.copyOfRange(frame, p, p + sigLen);
        return new MqttSecurityEnvelope(
                version, algId, keyId, producer, issuedAt, payload, signature);
    }

    private static void requireRemaining(byte[] frame, int offset, int required, String field) {
        if (required < 0 || offset < 0 || offset > frame.length
                || required > frame.length - offset) {
            throw new IllegalArgumentException("malformed envelope: " + field);
        }
    }

    private static int writeInt(byte[] out, int p, int v) {
        out[p++] = (byte) ((v >>> 24) & 0xFF);
        out[p++] = (byte) ((v >>> 16) & 0xFF);
        out[p++] = (byte) ((v >>> 8) & 0xFF);
        out[p++] = (byte) (v & 0xFF);
        return p;
    }

    private static int writeLong(byte[] out, int p, long v) {
        for (int i = 7; i >= 0; i--) {
            out[p++] = (byte) ((v >>> (i * 8)) & 0xFF);
        }
        return p;
    }

    private static int readInt(byte[] b, int p) {
        return ((b[p] & 0xFF) << 24)
                | ((b[p + 1] & 0xFF) << 16)
                | ((b[p + 2] & 0xFF) << 8)
                | (b[p + 3] & 0xFF);
    }

    private static long readLong(byte[] b, int p) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (b[p + i] & 0xFF);
        }
        return v;
    }
}
