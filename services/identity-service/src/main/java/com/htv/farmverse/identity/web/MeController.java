package com.htv.farmverse.identity.web;

import java.util.List;

import com.htv.farmverse.identity.account.AccountService;
import com.htv.farmverse.identity.account.UserAccount;
import com.htv.farmverse.identity.mfa.TotpService;
import com.htv.farmverse.service.security.FarmVerseClaims;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hồ sơ của chính người đang đăng nhập. Chỉ cần token hợp lệ.
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "Hồ sơ cá nhân", description = "Hồ sơ, mật khẩu, TOTP và mã khôi phục của chính người đang đăng nhập")
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

    private final AccountService accounts;
    private final TotpService totp;

    @GetMapping
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        return Me.of(accounts.get(jwt.getSubject()), jwt);
    }

    @PatchMapping
    public Me update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdate body) {
        return Me.of(accounts.updateProfile(jwt.getSubject(), body.displayName(), body.locale(), body.timeZone()), jwt);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChange body) {
        accounts.changePassword(jwt.getSubject(), body.currentPassword(), body.newPassword());
    }

    // ---- MFA TOTP ----

    @GetMapping("/mfa")
    public MfaStatus mfa(@AuthenticationPrincipal Jwt jwt) {
        boolean enabled = totp.enabled(jwt.getSubject());
        return new MfaStatus(enabled, enabled ? totp.recoveryCodesRemaining(jwt.getSubject()) : 0);
    }

    /**
     * Bước 1: tạo secret mới; app authenticator quét {@code otpauthUri} (mã QR).
     */
    @PostMapping("/mfa/totp")
    public TotpService.Enrollment beginTotp(@AuthenticationPrincipal Jwt jwt) {
        return totp.begin(jwt.getSubject());
    }

    /**
     * Bước 2: nhập mã đầu tiên để bật. Trả bộ mã khôi phục, chỉ hiện đúng một lần.
     */
    @PostMapping("/mfa/totp/confirm")
    public RecoveryCodes confirmTotp(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TotpCode body) {
        return new RecoveryCodes(totp.confirm(jwt.getSubject(), body.code()));
    }

    /**
     * Sinh lại bộ mã khôi phục (bộ cũ mất hiệu lực). Cần mã TOTP hiện tại.
     */
    @PostMapping("/mfa/recovery-codes")
    public RecoveryCodes regenerateRecoveryCodes(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TotpCode body) {
        return new RecoveryCodes(totp.regenerateRecoveryCodes(jwt.getSubject(), body.code()));
    }

    @DeleteMapping("/mfa/totp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableTotp(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TotpCode body) {
        totp.disable(jwt.getSubject(), body.code());
    }

    public record TotpCode(@NotBlank @jakarta.validation.constraints.Pattern(regexp = "\\d{6}") String code) {
    }

    public record RecoveryCodes(List<String> codes) {
    }

    public record MfaStatus(boolean totpEnabled, long recoveryCodesRemaining) {
    }

    public record ProfileUpdate(@Size(min = 1, max = 150) String displayName,
                                @Size(max = 20) String locale,
                                @Size(max = 50) String timeZone) {
    }

    public record PasswordChange(@NotBlank String currentPassword,
                                 @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    /**
     * Quyền lấy từ token đang dùng (đúng với những gì resource server khác thấy).
     */
    public record Me(String userId, String email, String displayName, String locale, String timeZone,
                     String tenantId, List<String> roles, List<String> permissions) {

        static Me of(UserAccount account, Jwt jwt) {
            return new Me(account.getId(), account.getEmail(), account.getDisplayName(), account.getLocale(),
                    account.getTimeZone(), jwt.getClaimAsString(FarmVerseClaims.TENANT_ID),
                    list(jwt, FarmVerseClaims.ROLES), list(jwt, FarmVerseClaims.PERMISSIONS));
        }

        private static List<String> list(Jwt jwt, String claim) {
            List<String> values = jwt.getClaimAsStringList(claim);
            return values == null ? List.of() : values;
        }
    }
}
