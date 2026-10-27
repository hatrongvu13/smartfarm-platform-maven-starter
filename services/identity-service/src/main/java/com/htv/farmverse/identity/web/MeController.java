package com.htv.farmverse.identity.web;

import java.util.List;

import com.htv.farmverse.identity.account.AccountService;
import com.htv.farmverse.identity.account.UserAccount;
import com.htv.farmverse.service.security.FarmVerseClaims;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Hồ sơ của chính người đang đăng nhập. Chỉ cần token hợp lệ. */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

    private final AccountService accounts;

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

    public record ProfileUpdate(@Size(min = 1, max = 150) String displayName,
                                @Size(max = 20) String locale,
                                @Size(max = 50) String timeZone) {
    }

    public record PasswordChange(@NotBlank String currentPassword,
                                 @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    /** Quyền lấy từ token đang dùng (đúng với những gì resource server khác thấy). */
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
