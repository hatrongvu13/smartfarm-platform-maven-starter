package com.htv.farmverse.identity.oauth2;

import java.util.ArrayList;
import java.util.List;

import com.htv.farmverse.identity.authorization.AccessService;
import com.htv.farmverse.service.security.FarmVerseClaims;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * Thêm claim FarmVerse vào token do SAS cấp.
 *
 * <p>Access token (mọi grant): {@code aud} = danh sách resource server ({@code farmverse.identity.token-audiences},
 * mặc định {@code farm-service}, {@code identity-service}; ADR-0006), {@code token_type}.
 * Riêng token của người dùng: {@code sub} = id tài khoản, {@code tenant_id}, {@code membership_id},
 * {@code roles}, {@code perms} — tính một lần lúc cấp, resource server không phải hỏi lại.
 *
 * <p>ID token: giữ {@code sub} = principal của SAS (membership id) cho khớp /userinfo;
 * thêm {@code user_id}, {@code tenant_id} và email / tên theo scope.
 */
@Component
public class FarmVerseTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final AccessService access;
    private final List<String> audiences;

    public FarmVerseTokenCustomizer(AccessService access,
                                    @Value("${farmverse.identity.token-audiences:"
                                            + FarmVerseClaims.FARM_AUDIENCE + "," + FarmVerseClaims.IDENTITY_AUDIENCE + "}")
                                    List<String> audiences) {
        if (audiences.isEmpty()) {
            throw new IllegalArgumentException("farmverse.identity.token-audiences không được rỗng");
        }
        this.access = access;
        this.audiences = List.copyOf(audiences);
    }

    /** Membership bị tắt / tạm khóa sau khi đăng nhập: từ chối cấp (và refresh) token. */
    private AccessService.TenantAccess grantFor(JwtEncodingContext context) {
        try {
            return access.accessOf(context.getPrincipal().getName());
        } catch (IllegalStateException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, e.getMessage(), null));
        }
    }

    @Override
    public void customize(JwtEncodingContext context) {
        boolean clientCredentials = AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType());

        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getClaims()
                    .audience(mutable(audiences))
                    .claim(FarmVerseClaims.TOKEN_TYPE, clientCredentials ? "service" : "user");
            if (!clientCredentials) {
                AccessService.TenantAccess grant = grantFor(context);
                context.getClaims()
                        .subject(grant.userId())
                        .claim(FarmVerseClaims.TENANT_ID, grant.tenantId())
                        .claim(FarmVerseClaims.MEMBERSHIP_ID, grant.membershipId())
                        .claim(FarmVerseClaims.ROLES, mutable(grant.roles()))
                        .claim(FarmVerseClaims.PERMISSIONS, mutable(grant.permissions()));
            }
            return;
        }

        if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            AccessService.TenantAccess grant = grantFor(context);
            var claims = context.getClaims()
                    .claim("user_id", grant.userId())
                    .claim(FarmVerseClaims.TENANT_ID, grant.tenantId())
                    .claim("tenant_code", grant.tenantCode());
            if (context.getAuthorizedScopes().contains(OidcScopes.EMAIL)) {
                claims.claim("email", grant.email());
            }
            if (context.getAuthorizedScopes().contains(OidcScopes.PROFILE)) {
                claims.claim("name", grant.displayName());
            }
        }
    }

    /**
     * Claim dạng danh sách phải là {@link ArrayList}. SAS lưu claim vào cột JSON của {@code oauth2_authorization}
     * kèm tên kiểu Java; Jackson chỉ cho đọc lại các kiểu trong danh sách cho phép. {@code List.of(...)} và
     * {@code Stream.toList()} cho {@code ImmutableCollections$List12} bị từ chối, làm refresh token lỗi.
     */
    private static <T> List<T> mutable(java.util.Collection<T> values) {
        return new ArrayList<>(values);
    }
}
