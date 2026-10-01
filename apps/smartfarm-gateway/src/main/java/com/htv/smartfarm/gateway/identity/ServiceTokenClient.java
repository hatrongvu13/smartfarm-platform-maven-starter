package com.htv.smartfarm.gateway.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ServiceTokenClient {
    private final WebClient identity;
    private final String clientId;
    private final String secret;
    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Key, Object> locks = new ConcurrentHashMap<>();

    public ServiceTokenClient(WebClient identityWebClient,
            @Value("${smartfarm.gateway.service-client.id:gateway}") String clientId,
            @Value("${smartfarm.gateway.service-client.secret:}") String secret) {
        this.identity = identityWebClient;
        this.clientId = required(clientId, "clientId");
        this.secret = secret;
    }

    private record Key(String audience, String tenantId) {
        private Key {
            audience = required(audience, "audience");
            tenantId = required(tenantId, "tenantId");
        }
    }
    private record Cached(String token, Instant refreshAfter) { }
    private record Grant(String accessToken, String tokenType, long expiresInSeconds, String audience) { }
    private record TokenRequest(String clientId, String secret, String audience, String tenantId, String actorId) { }

    public String tokenFor(String audience, String tenantId, String actorId) {
        Key key = new Key(audience, tenantId);
        Cached current = cache.get(key);
        if (valid(current)) return current.token();
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            current = cache.get(key);
            if (valid(current)) return current.token();
            if (secret == null || secret.isBlank())
                throw new IllegalStateException("gateway service-client secret is not configured");
            Grant grant = identity.post().uri("/internal/service-token")
                    .bodyValue(new TokenRequest(clientId, secret, audience, tenantId, actorId))
                    .retrieve().bodyToMono(Grant.class).block(Duration.ofSeconds(5));
            if (grant == null || grant.accessToken() == null || grant.accessToken().isBlank())
                throw new IllegalStateException("identity returned no service token");
            long life = Math.max(2, grant.expiresInSeconds());
            cache.put(key, new Cached(grant.accessToken(), Instant.now().plusSeconds(Math.max(1, life * 3 / 4))));
            return grant.accessToken();
        }
    }

    public void invalidate(String audience, String tenantId) {
        Key key = new Key(audience, tenantId); cache.remove(key); locks.remove(key);
    }
    public void invalidate(String audience) {
        cache.keySet().removeIf(k -> k.audience().equals(audience));
        locks.keySet().removeIf(k -> k.audience().equals(audience));
    }
    private static boolean valid(Cached value) {
        return value != null && Instant.now().isBefore(value.refreshAfter());
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
