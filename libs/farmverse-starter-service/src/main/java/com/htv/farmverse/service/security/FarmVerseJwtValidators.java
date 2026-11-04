package com.htv.farmverse.service.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

/** Bộ kiểm token chuẩn cho resource server: chữ ký + thời hạn + issuer (mặc định của Spring) và audience. */
public final class FarmVerseJwtValidators {

    private FarmVerseJwtValidators() {
    }

    /**
     * Token hợp lệ khi {@code aud} chứa ÍT NHẤT MỘT giá trị trong {@code accepted}. Thường chỉ một giá trị (audience của
     * chính service); thêm giá trị thứ hai chỉ trong lúc chuyển đổi (ví dụ {@link FarmVerseClaims#LEGACY_API_AUDIENCE}).
     */
    public static OAuth2TokenValidator<Jwt> issuerAndAudience(String issuer, Collection<String> accepted) {
        Set<String> audiences = Set.copyOf(accepted);
        if (audiences.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một audience được nhận");
        }
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && !Collections.disjoint(aud, audiences));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
    }
}
