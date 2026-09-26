package com.htv.smartfarm.gateway.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Obtains and caches machine-to-machine (service) tokens from the internal identity
 * endpoint (Hướng A). The gateway authenticates as the {@code gateway} client and asks
 * for a token scoped to ONE downstream audience; the token is cached per audience and
 * proactively refreshed shortly before it expires, so downstream gRPC calls never carry
 * a user's own access token. The human actor is passed only for audit correlation and
 * is never embedded in the token — it travels in {@code RequestContext.actor_id}.
 */
@Component
public class ServiceTokenClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenClient.class);

    private final WebClient identityClient;
    private final String clientId;
    private final String secret;

    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public ServiceTokenClient(WebClient identityWebClient,
                              @Value("${smartfarm.gateway.service-client.id:gateway}") String clientId,
                              @Value("${smartfarm.gateway.service-client.secret:}") String secret) {
        this.identityClient = identityWebClient;
        this.clientId = clientId;
        this.secret = secret;
    }

    private record CachedToken(String token, Instant refreshAfter) {
    }

    private record Grant(String accessToken, String tokenType, long expiresInSeconds, String audience) {
    }

    private record TokenRequest(String clientId, String secret, String audience, String tenantId, String actorId) {
    }

    /**
     * Return a valid service token for {@code audience}, fetching (or refreshing) it if the
     * cached one is missing or near expiry. Blocks briefly on a fetch — call from a
     * bounded-elastic scheduler in reactive code.
     *
     * @param tenantId tenant the downstream call operates within
     * @param actorId  the human actor (subject of the verified user JWT) — audit only
     */
    public String tokenFor(String audience, String tenantId, String actorId) {
        CachedToken cached = cache.get(audience);
        if (cached != null && Instant.now().isBefore(cached.refreshAfter())) {
            return cached.token();
        }
        return fetch(audience, tenantId, actorId);
    }

    private synchronized String fetch(String audience, String tenantId, String actorId) {
        // Re-check under lock: another thread may have refreshed while we waited.
        CachedToken cached = cache.get(audience);
        if (cached != null && Instant.now().isBefore(cached.refreshAfter())) {
            return cached.token();
        }
        if (secret == null || secret.isBlank())
            throw new IllegalStateException("gateway service-client secret is not configured");

        Grant grant = identityClient.post()
                .uri("/internal/service-token")
                .bodyValue(new TokenRequest(clientId, secret, audience, tenantId, actorId))
                .retrieve()
                .bodyToMono(Grant.class)
                .block(Duration.ofSeconds(5));

        if (grant == null || grant.accessToken() == null || grant.accessToken().isBlank())
            throw new IllegalStateException("identity returned no service token for audience " + audience);

        // Refresh at 75% of the token's lifetime to avoid using an about-to-expire token.
        long refreshInS = Math.max(5, (grant.expiresInSeconds() * 3) / 4);
        cache.put(audience, new CachedToken(grant.accessToken(), Instant.now().plusSeconds(refreshInS)));
        log.debug("Fetched service token audience={} tenant={} expires_in_s={}", audience, tenantId, grant.expiresInSeconds());
        return grant.accessToken();
    }

    /** Invalidate a cached token (e.g. after a downstream UNAUTHENTICATED) so the next call refetches. */
    public void invalidate(String audience) {
        cache.remove(audience);
    }
}
