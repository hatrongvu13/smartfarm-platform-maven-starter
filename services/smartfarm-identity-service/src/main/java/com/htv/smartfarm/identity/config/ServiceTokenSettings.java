package com.htv.smartfarm.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration for machine-to-machine (service) tokens issued under the OAuth2
 * client-credentials style flow (Hướng A).
 *
 * <p>Each entry in {@code clients} is a WHITELIST: only a registered service
 * caller (e.g. {@code gateway}) may request a token, and only for an audience it
 * is explicitly allowed to call, carrying at most the scopes granted here. This
 * prevents a compromised caller from minting a broad token for an arbitrary
 * service — the identity service is the single authority that decides which
 * caller may reach which service and with which privileges.
 *
 * <p>The subject of a service token is {@code svc:<clientId>} so audit logs can
 * tell a machine caller apart from a human user. The original human actor is NOT
 * carried in the token; it travels in {@code RequestContext.actor_id}, set by the
 * gateway after it has verified the user's JWT, and is logged at each hop so a
 * chain of calls can be traced back to the user who triggered it.
 */
@ConfigurationProperties(prefix = "smartfarm.identity.service-token")
public record ServiceTokenSettings(Duration ttl, Map<String, Client> clients) {

    public ServiceTokenSettings {
        // Service tokens are short-lived by design; RsaJwtIssuer caps this at 15 minutes.
        if (ttl == null || ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(15)) > 0)
            throw new IllegalArgumentException("service-token.ttl must be between 1 second and 15 minutes");
        clients = clients == null ? Map.of() : Map.copyOf(clients);
        clients.forEach((id, c) -> {
            if (id == null || id.isBlank())
                throw new IllegalArgumentException("service-token client id must not be blank");
            if (c == null || c.secret() == null || c.secret().isBlank())
                throw new IllegalArgumentException("service-token client '" + id + "' requires a secret");
            if (c.audiences() == null || c.audiences().isEmpty())
                throw new IllegalArgumentException("service-token client '" + id + "' requires at least one allowed audience");
            c.scopesByAudience().forEach((audience, scopes) -> {
                if (!c.audiences().contains(audience))
                    throw new IllegalArgumentException("service-token client '" + id
                            + "' configures scopes for a non-whitelisted audience: " + audience);
                if (scopes.stream().anyMatch(ServiceTokenSettings::isWildcard))
                    throw new IllegalArgumentException("service-token client '" + id
                            + "' must not receive wildcard scope");
            });
        });
    }

    private static boolean isWildcard(String scope) {
        if (scope == null) return false;
        String normalized = scope.trim();
        return "*".equals(normalized) || "SCOPE_*".equalsIgnoreCase(normalized);
    }

    /** Whitelisted caller: its shared secret, the audiences it may target, and the scopes it may receive per audience. */
    public record Client(String secret, List<String> audiences, Map<String, List<String>> scopesByAudience) {
        public Client {
            audiences = audiences == null ? List.of() : List.copyOf(audiences);
            scopesByAudience = scopesByAudience == null ? Map.of() : Map.copyOf(scopesByAudience);
        }

        public boolean mayTarget(String audience) {
            return audiences.contains(audience);
        }

        public List<String> scopesFor(String audience) {
            return scopesByAudience.getOrDefault(audience, List.of());
        }
    }
}
