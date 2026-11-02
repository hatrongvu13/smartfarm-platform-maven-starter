package com.htv.farmverse.identity.mfa;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.htv.farmverse.identity.audit.SecurityAudit;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mã khôi phục 2FA cho người mất thiết bị authenticator.
 *
 * <ul>
 *   <li>10 mã mỗi bộ, dạng {@code ABCDE-FGHJK}: 10 ký tự từ bảng 31 ký tự (bỏ 0 O 1 I L dễ nhầm), khoảng 50 bit.</li>
 *   <li>Chỉ lưu bản băm bằng {@link PasswordEncoder} của hệ thống. Mã thô chỉ hiện đúng một lần, lúc sinh.</li>
 *   <li>Mỗi mã dùng được một lần. Sinh bộ mới thì bộ cũ mất hiệu lực.</li>
 * </ul>
 */
@Service
public class RecoveryCodeService {

    static final int COUNT = 10;
    static final int LENGTH = 10;
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final RecoveryCodeRepository codes;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RecoveryCodeService(RecoveryCodeRepository codes, PasswordEncoder encoder, Clock clock) {
        this.codes = codes;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Sinh bộ mới, xóa bộ cũ. Trả mã thô để hiện cho người dùng một lần. */
    @Transactional
    public List<String> generate(String userId) {
        codes.deleteByUserId(userId);
        codes.flush();
        List<String> display = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            String raw = randomCode();
            codes.save(new RecoveryCode(userId, encoder.encode(raw)));
            display.add(raw.substring(0, LENGTH / 2) + "-" + raw.substring(LENGTH / 2));
        }
        SecurityAudit.info("mfa.recovery.generated", "target", userId);
        return display;
    }

    @Transactional(readOnly = true)
    public long remaining(String userId) {
        return codes.countByUserIdAndUsedAtIsNull(userId);
    }

    /** Dùng một mã khôi phục. Đúng thì mã bị đánh dấu đã dùng. Chấp nhận chữ thường, dấu gạch và khoảng trắng. */
    @Transactional
    public boolean consume(String userId, String input) {
        String normalized = normalize(input);
        if (normalized == null) {
            return false;
        }
        List<RecoveryCode> unused = codes.findByUserIdAndUsedAtIsNull(userId);
        for (RecoveryCode candidate : unused) {
            if (encoder.matches(normalized, candidate.getCodeHash())) {
                candidate.markUsed(clock.instant());
                SecurityAudit.warn("mfa.recovery.used", "target", userId, "remaining", unused.size() - 1);
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void deleteAll(String userId) {
        codes.deleteByUserId(userId);
    }

    /** @return mã chuẩn hóa 10 ký tự, hoặc null nếu không đúng dạng (khỏi tốn thời gian băm) */
    static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String cleaned = input.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (cleaned.length() != LENGTH) {
            return null;
        }
        for (int i = 0; i < cleaned.length(); i++) {
            if (ALPHABET.indexOf(cleaned.charAt(i)) < 0) {
                return null;
            }
        }
        return cleaned;
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
