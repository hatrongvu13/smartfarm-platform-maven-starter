package com.htv.smartfarm.order.grpc;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fetches and caches machine-to-machine (service) tokens for the order saga (Phase 2, Hướng A).
 * The saga calls inventory and finance as the {@code order} service client, NOT with any user's
 * token; it requests a token scoped to the single downstream audience it is about to call. Tokens
 * are cached per audience and refreshed before expiry. The human actor that triggered the order is
 * passed for audit correlation only and travels separately in RequestContext.actor_id.
 */
@Component
public class ServiceTokenClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenClient.class);

    private final RestClient identity;
    private final String clientId;
    private final String secret;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public ServiceTokenClient(@Value("${smartfarm.identity.base-url:http://localhost:8092}") String identityBaseUrl,
                              @Value("${smartfarm.order.service-client.id:order}") String clientId,
                              @Value("${smartfarm.order.service-client.secret:}") String secret) {
        this.identity = RestClient.builder().baseUrl(identityBaseUrl).build();
        this.clientId = clientId;
        this.secret = secret;
    }

    private record Cached(String token, Instant refreshAfter) {
    }

    private record TokenRequest(String clientId, String secret, String audience, String tenantId, String actorId) {
    }

    private record Grant(String accessToken, String tokenType, long expiresInSeconds, String audience) {
    }

    public String tokenFor(String audience, String tenantId, String actorId) {
        Cached c = cache.get(audience);
        if (c != null && Instant.now().isBefore(c.refreshAfter())) return c.token();
        return fetch(audience, tenantId, actorId);
    }

    private synchronized String fetch(String audience, String tenantId, String actorId) {
        Cached c = cache.get(audience);
        if (c != null && Instant.now().isBefore(c.refreshAfter())) return c.token();
        if (secret == null || secret.isBlank())
            throw new IllegalStateException("order service-client secret is not configured");

        Grant grant = identity.post().uri("/internal/service-token")
                .body(new TokenRequest(clientId, secret, audience, tenantId, actorId))
                .retrieve().body(Grant.class);

        if (grant == null || grant.accessToken() == null || grant.accessToken().isBlank())
            throw new IllegalStateException("identity returned no service token for audience " + audience);

        long refreshInS = Math.max(5, (grant.expiresInSeconds() * 3) / 4);
        cache.put(audience, new Cached(grant.accessToken(), Instant.now().plusSeconds(refreshInS)));
        log.debug("Fetched service token audience={} tenant={} expires_in_s={}", audience, tenantId, grant.expiresInSeconds());
        return grant.accessToken();
    }

    public void invalidate(String audience) {
        cache.remove(audience);
    }
}
