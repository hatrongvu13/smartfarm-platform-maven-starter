package com.htv.farmverse.gateway.config;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Gateway là OIDC client của identity, prefix {@code farmverse.gateway.oidc}.
 * Không dùng discovery lúc khởi động: endpoint suy ra từ {@code issuer} theo đường mặc định của
 * Spring Authorization Server, nên gateway khởi động được kể cả khi identity chưa chạy.
 *
 * @param sslBundle bundle chứng chỉ client (mTLS) cho mọi cuộc gọi tới identity; để trống = không mTLS (test)
 */
@Validated
@ConfigurationProperties("farmverse.gateway.oidc")
public record GatewayOidcProperties(
        @NotBlank String issuer,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotEmpty Set<String> scopes,
        String sslBundle
) {

    public String authorizationUri() { return issuer + "/oauth2/authorize"; }
    public String tokenUri() { return issuer + "/oauth2/token"; }
    public String jwkSetUri() { return issuer + "/oauth2/jwks"; }
    public String endSessionUri() { return issuer + "/connect/logout"; }

    public boolean mtls() {
        return sslBundle != null && !sslBundle.isBlank();
    }

    @Override
    public String toString() {
        return "GatewayOidcProperties[issuer=" + issuer + ", clientId=" + clientId + ", scopes=" + List.copyOf(scopes)
                + ", sslBundle=" + sslBundle + ", clientSecret=[REDACTED]]";
    }
}
