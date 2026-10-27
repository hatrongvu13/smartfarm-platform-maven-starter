package com.htv.farmverse.identity.mfa;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;

/**
 * TOTP theo RFC 6238 (HMAC-SHA1, bước 30 giây, 6 chữ số) — phần duy nhất của MFA tự viết,
 * vì Spring Security không có sẵn. Chấp nhận lệch đồng hồ ±1 bước.
 */
public final class TotpCodes {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;
    private static final int WINDOW = 1;
    private static final Base32 BASE32 = new Base32();
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpCodes() {
    }

    /** Secret 160 bit, mã Base32 (không dấu =) để nhập vào app authenticator. */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return BASE32.encodeToString(bytes).replace("=", "");
    }

    public static long stepAt(Instant now) {
        return now.getEpochSecond() / STEP_SECONDS;
    }

    /**
     * @return bước thời gian khớp mã (để chống dùng lại), hoặc -1 nếu sai
     */
    public static long verify(String base32Secret, String code, Instant now) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return -1;
        }
        byte[] key = BASE32.decode(base32Secret);
        long current = stepAt(now);
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if (constantTimeEquals(code(key, step), code)) {
                return step;
            }
        }
        return -1;
    }

    /** Mã tại một bước (dùng cho test). */
    public static String codeAt(String base32Secret, long step) {
        return code(BASE32.decode(base32Secret), step);
    }

    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
