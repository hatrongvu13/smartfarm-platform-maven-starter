package com.htv.farmverse.gateway.bff;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.htv.farmverse.gateway.config.SecurityConfiguration;

/**
 * Thông tin người dùng cho SPA. Trả claim đã có trong token, không bao giờ trả chính token.
 */
@RestController
public class BffController {

    @GetMapping("/bff/me")
    public Me me(@AuthenticationPrincipal OidcUser user,
                 @RegisteredOAuth2AuthorizedClient(SecurityConfiguration.REGISTRATION_ID) OAuth2AuthorizedClient client)
            throws ParseException {
        // Access token do identity cấp trực tiếp cho gateway qua kênh back-channel: chỉ đọc claim để hiển thị.
        // Quyết định quyền thật vẫn do service phía sau kiểm chữ ký.
        JWTClaimsSet access = JWTParser.parse(client.getAccessToken().getTokenValue()).getJWTClaimsSet();
        return new Me(
                access.getSubject(),
                user.getEmail(),
                user.getFullName(),
                access.getStringClaim("tenant_id"),
                user.getClaimAsString("tenant_code"),
                list(access.getClaims(), "roles"),
                list(access.getClaims(), "perms"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value instanceof List<?> items ? (List<String>) items : List.of();
    }

    public record Me(String userId, String email, String name, String tenantId, String tenantCode,
                     List<String> roles, List<String> permissions) {
    }
}
