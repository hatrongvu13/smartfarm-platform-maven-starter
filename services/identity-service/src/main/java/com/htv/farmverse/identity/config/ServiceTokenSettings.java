package com.htv.farmverse.identity.config;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
 * service. The identity service is the single authority that decides which
 * caller may reach which service and with which privileges.
 *
 * <p>The subject of a service token is {@code svc:<clientId>} so audit logs can
 * tell a machine caller apart from a human user. The original human actor is NOT
 * carried in the token. It travels in {@code RequestContext.actor_id}, set by
 * the gateway after it has verified the user's JWT, and is logged at each hop so
 * a chain of calls can be traced back to the user who triggered it.
 */
@Slf4j
@ConfigurationProperties(prefix = "farmverse.identity.service-token")
public record ServiceTokenSettings(
        Duration ttl,
        Map<String, Client> clients
) {

    public ServiceTokenSettings {

        if (ttl == null
                || ttl.isNegative()
                || ttl.isZero()
                || ttl.compareTo(Duration.ofMinutes(15)) > 0) {

            log.warn(
                    "Invalid service token TTL configuration: ttl={}",
                    ttl
            );

            throw new IllegalArgumentException(
                    "service-token.ttl must be between 1 second and 15 minutes"
            );
        }

        clients = clients == null
                ? Map.of()
                : Map.copyOf(clients);

        log.info(
                "Initializing service token settings: ttl={}, clientCount={}",
                ttl,
                clients.size()
        );

        clients.forEach((clientId, client) -> {

            if (clientId == null || clientId.isBlank()) {

                log.warn(
                        "Service token configuration contains blank client id"
                );

                throw new IllegalArgumentException(
                        "service-token client id must not be blank"
                );
            }

            if (client == null
                    || client.secret() == null
                    || client.secret().isBlank()) {

                log.warn(
                        "Service token client configuration is missing secret: clientId={}",
                        clientId
                );

                throw new IllegalArgumentException(
                        "service-token client '"
                                + clientId
                                + "' requires a secret"
                );
            }

            if (client.audiences() == null
                    || client.audiences().isEmpty()) {

                log.warn(
                        "Service token client configuration is missing audiences: clientId={}",
                        clientId
                );

                throw new IllegalArgumentException(
                        "service-token client '"
                                + clientId
                                + "' requires at least one allowed audience"
                );
            }

            client.scopesByAudience().forEach(
                    (audience, scopes) -> {

                        if (!client.audiences().contains(audience)) {

                            log.warn(
                                    "Service token client configures scopes for non-whitelisted audience: "
                                            + "clientId={}, audience={}",
                                    clientId,
                                    audience
                            );

                            throw new IllegalArgumentException(
                                    "service-token client '"
                                            + clientId
                                            + "' configures scopes for a non-whitelisted audience: "
                                            + audience
                            );
                        }

                        if (scopes.stream()
                                .anyMatch(ServiceTokenSettings::isWildcard)) {

                            log.warn(
                                    "Service token client contains wildcard scope: "
                                            + "clientId={}, audience={}",
                                    clientId,
                                    audience
                            );

                            throw new IllegalArgumentException(
                                    "service-token client '"
                                            + clientId
                                            + "' must not receive wildcard scope"
                            );
                        }

                        log.debug(
                                "Validated audience scope configuration: "
                                        + "clientId={}, audience={}, scopeCount={}",
                                clientId,
                                audience,
                                scopes.size()
                        );
                    }
            );

            log.debug(
                    "Validated service token client configuration: "
                            + "clientId={}, audienceCount={}, delegationEnabled={}",
                    clientId,
                    client.audiences().size(),
                    client.delegationEnabled()
            );
        });

        log.info(
                "Service token settings initialized successfully: clientCount={}",
                clients.size()
        );
    }

    private static boolean isWildcard(String scope) {

        if (scope == null) {
            return false;
        }

        String normalized = scope.trim();

        return "*".equals(normalized)
                || "SCOPE_*".equalsIgnoreCase(normalized);
    }

    /**
     * Whitelisted caller: its shared secret, the audiences it may target,
     * and the scopes it may receive per audience.
     */
    public record Client(
            String secret,
            List<String> audiences,
            Map<String, List<String>> scopesByAudience,
            boolean delegationEnabled
    ) {

        /**
         * Compatibility constructor:
         * delegation remains disabled unless explicitly configured.
         */
        public Client(
                String secret,
                List<String> audiences,
                Map<String, List<String>> scopesByAudience
        ) {
            this(
                    secret,
                    audiences,
                    scopesByAudience,
                    false
            );
        }

        @ConstructorBinding
        public Client {

            audiences = audiences == null
                    ? List.of()
                    : List.copyOf(audiences);

            scopesByAudience = scopesByAudience == null
                    ? Map.of()
                    : Map.copyOf(scopesByAudience);

            log.debug(
                    "Initialized service token client configuration: "
                            + "audienceCount={}, scopesByAudienceCount={}, "
                            + "delegationEnabled={}",
                    audiences.size(),
                    scopesByAudience.size(),
                    delegationEnabled
            );
        }

        public boolean mayTarget(String audience) {
            return audiences.contains(audience);
        }

        public List<String> scopesFor(String audience) {
            return scopesByAudience.getOrDefault(
                    audience,
                    List.of()
            );
        }
    }
}