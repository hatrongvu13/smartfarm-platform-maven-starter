package com.htv.smartfarm.identity.config;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "smartfarm.identity.oauth2")
public record IdentityOAuthProperties(
        @Valid List<Client> clients
) {
    public IdentityOAuthProperties {
        clients = clients == null ? List.of() : List.copyOf(clients);
    }

    public record Client(
            @NotBlank String clientId,
            @NotBlank @Size(min = 32) String clientSecret,
            @NotBlank String name,
            @NotEmpty Set<@NotBlank String> grantTypes,
            Set<String> redirectUris,
            Set<String> postLogoutRedirectUris,
            @NotEmpty Set<@NotBlank String> scopes,
            @NotEmpty Set<@NotBlank String> audiences,
            boolean requireConsent
    ) {
        public Client {
            redirectUris = redirectUris == null ? Set.of() : Set.copyOf(redirectUris);
            postLogoutRedirectUris = postLogoutRedirectUris == null
                    ? Set.of()
                    : Set.copyOf(postLogoutRedirectUris);
            grantTypes = grantTypes == null ? Set.of() : Set.copyOf(grantTypes);
            scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
            audiences = audiences == null ? Set.of() : Set.copyOf(audiences);

            if (grantTypes.contains("authorization_code") && redirectUris.isEmpty()) {
                throw new IllegalArgumentException(
                        "OAuth authorization_code clients require exact redirect URIs"
                );
            }
            if (!grantTypes.contains("authorization_code") && !redirectUris.isEmpty()) {
                throw new IllegalArgumentException(
                        "Redirect URIs are only valid for authorization_code clients"
                );
            }
            if (audiences.size() != 1) {
                throw new IllegalArgumentException(
                        "Each OAuth client must be restricted to exactly one resource audience"
                );
            }
            if (grantTypes.contains("authorization_code")
                    && !scopes.contains("openid")) {
                throw new IllegalArgumentException(
                        "OIDC authorization_code clients must allow the openid scope"
                );
            }
        }

        @Override
        public String toString() {
            return "Client[clientId=" + clientId
                    + ", name=" + name
                    + ", grantTypes=" + grantTypes
                    + ", audiences=" + audiences
                    + ", secret=[REDACTED]]";
        }
    }
}
