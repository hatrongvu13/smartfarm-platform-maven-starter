package com.htv.farmverse.identity.mfa;

import java.time.Clock;

import com.htv.farmverse.identity.account.AccountService;
import com.htv.farmverse.identity.config.IdentityProperties;
import com.htv.farmverse.identity.tenant.MembershipRepository;
import com.htv.farmverse.service.web.ApiProblem;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vòng đời TOTP: đăng ký → xác nhận → dùng khi đăng nhập → tắt / admin reset.
 * Secret lưu đã mã hóa bằng {@link Encryptors#stronger} (AES-GCM, Spring Security Crypto).
 */
@Service
public class TotpService {

    public static final String WRONG_CODE = "SF-IDENTITY-020";
    public static final String NOT_ENROLLED = "SF-IDENTITY-021";
    public static final String ALREADY_ENABLED = "SF-IDENTITY-022";

    private final UserTotpRepository totps;
    private final MembershipRepository memberships;
    private final AccountService accounts;
    private final TextEncryptor encryptor;
    private final String issuer;
    private final Clock clock;

    public TotpService(UserTotpRepository totps, MembershipRepository memberships, AccountService accounts,
                       IdentityProperties properties, Clock clock) {
        this.totps = totps;
        this.memberships = memberships;
        this.accounts = accounts;
        IdentityProperties.Mfa mfa = properties.mfa();
        this.encryptor = Encryptors.delux(mfa.encryptionPassword(), mfa.encryptionSalt());
        this.issuer = mfa.issuer();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean enabled(String userId) {
        return totps.findById(userId).map(UserTotp::isConfirmed).orElse(false);
    }

    /** Principal của phiên đăng nhập là membership id; TOTP gắn với tài khoản. */
    @Transactional(readOnly = true)
    public String userIdOfMembership(String membershipId) {
        return memberships.findById(membershipId).map(m -> m.getUser().getId()).orElse(null);
    }

    /** Bước 1 đăng ký: tạo secret mới, trả URI để quét QR. */
    @Transactional
    public Enrollment begin(String userId) {
        String secret = TotpCodes.newSecret();
        UserTotp totp = totps.findById(userId).orElse(null);
        if (totp != null && totp.isConfirmed()) {
            throw ApiProblem.conflict(ALREADY_ENABLED, "TOTP đã bật; tắt trước khi đăng ký lại");
        }
        if (totp == null) {
            totps.save(new UserTotp(userId, encryptor.encrypt(secret)));
        } else {
            totp.restart(encryptor.encrypt(secret));
        }
        String account = accounts.get(userId).getEmail();
        return new Enrollment(secret, TotpCodes.otpauthUri(issuer, account, secret));
    }

    /** Bước 2 đăng ký: nhập đúng mã đầu tiên thì bật. */
    @Transactional
    public void confirm(String userId, String code) {
        UserTotp totp = totps.findById(userId)
                .orElseThrow(() -> ApiProblem.badRequest(NOT_ENROLLED, "Chưa bắt đầu đăng ký TOTP"));
        long step = TotpCodes.verify(encryptor.decrypt(totp.getSecretCipher()), code, clock.instant());
        if (step < 0) {
            throw ApiProblem.badRequest(WRONG_CODE, "Mã không đúng");
        }
        totp.confirm(clock.instant(), step);
    }

    /** Tắt TOTP: phải chứng minh vẫn giữ authenticator. */
    @Transactional
    public void disable(String userId, String code) {
        if (!verify(userId, code)) {
            throw ApiProblem.badRequest(WRONG_CODE, "Mã không đúng");
        }
        totps.deleteById(userId);
    }

    /** Admin xóa TOTP khi người dùng mất thiết bị. */
    @Transactional
    public void reset(String userId) {
        totps.deleteById(userId);
    }

    /**
     * Kiểm mã khi đăng nhập. Sai thì tính vào bộ đếm khóa tạm của tài khoản;
     * mã đúng nhưng đã dùng trong bước 30 giây này cũng bị từ chối.
     */
    @Transactional
    public boolean verify(String userId, String code) {
        UserTotp totp = totps.findById(userId).filter(UserTotp::isConfirmed).orElse(null);
        if (totp == null) {
            return false;
        }
        long step = TotpCodes.verify(encryptor.decrypt(totp.getSecretCipher()), code, clock.instant());
        boolean ok = step >= 0 && totp.accept(step);
        if (!ok) {
            accounts.recordFactorFailure(userId);
        }
        return ok;
    }

    public record Enrollment(String secret, String otpauthUri) {
    }
}
