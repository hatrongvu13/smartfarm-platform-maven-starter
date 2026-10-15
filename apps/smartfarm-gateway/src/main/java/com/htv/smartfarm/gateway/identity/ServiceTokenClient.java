package com.htv.smartfarm.gateway.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.htv.smartfarm.security.authorization.AuthorizationVersionWatermarkStore;
import com.htv.smartfarm.security.authorization.InMemoryAuthorizationVersionWatermarkStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Gateway client for Identity service-token minting.
 *
 * <p>User-originated calls should use {@link #tokenFor(String, Jwt)}. That path derives actor,
 * tenant and authorization versions only from the verified JWT and caches by the complete signed
 * tuple. The legacy three-argument overload is retained for source compatibility, but deliberately
 * bypasses the cache so Identity revalidates the actor on every call.</p>
 */
@Component
public class ServiceTokenClient {
    private final WebClient identity;
    private final String clientId;
    private final String secret;
    private final String identityInternalApiSecret;
    private final Duration requestTimeout;
    private final AuthorizationVersionWatermarkStore authorizationWatermarks;
    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Key, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public ServiceTokenClient(
            WebClient identityWebClient,
            @Value("${smartfarm.gateway.service-client.id:gateway}") String clientId,
            @Value("${smartfarm.gateway.service-client.secret:}") String secret,
            @Value("${smartfarm.identity.internal-api-secret:}") String identityInternalApiSecret,
            @Value("${smartfarm.gateway.grpc.service-token-timeout:5s}") Duration requestTimeout,
            AuthorizationVersionWatermarkStore authorizationWatermarks
    ) {
        this.identity = java.util.Objects.requireNonNull(identityWebClient, "identityWebClient");
        this.clientId = required(clientId, "clientId");
        this.secret = secret;
        this.identityInternalApiSecret = identityInternalApiSecret == null
                ? ""
                : identityInternalApiSecret;
        this.requestTimeout = positive(requestTimeout, "service token timeout");
        this.authorizationWatermarks = java.util.Objects.requireNonNull(
                authorizationWatermarks,
                "authorizationWatermarks"
        );
    }

    /**
     * Compatibility constructor for manual construction without transport credentials.
     * Token minting fails closed until the internal API secret is supplied.
     */
    public ServiceTokenClient(
            WebClient identityWebClient,
            String clientId,
            String secret,
            Duration requestTimeout
    ) {
        this(
                identityWebClient,
                clientId,
                secret,
                "",
                requestTimeout,
                new InMemoryAuthorizationVersionWatermarkStore()
        );
    }

    private record Key(
            String audience,
            String tenantId,
            String actorId,
            long authorizationVersion,
            long tenantAuthorizationVersion
    ) {
        private Key {
            audience = required(audience, "audience");
            tenantId = required(tenantId, "tenantId");
            actorId = required(actorId, "actorId");
            nonNegative(authorizationVersion, "authorizationVersion");
            nonNegative(tenantAuthorizationVersion, "tenantAuthorizationVersion");
        }
    }

    private record Cached(String token, Instant refreshAfter) {
    }

    private record Grant(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            String audience
    ) {
    }

    private record TokenRequest(
            String clientId,
            String secret,
            String audience,
            String tenantId,
            String actorId
    ) {
    }

    /** Preferred delegated flow: all identity and version data comes from the verified JWT. */
    public String tokenFor(String audience, Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("jwt must not be null");
        }
        return tokenFor(
                audience,
                required(jwt.getClaimAsString("tenant_id"), "tenantId"),
                required(jwt.getSubject(), "actorId"),
                requiredLongClaim(jwt, "authorization_version"),
                requiredLongClaim(jwt, "tenant_authorization_version")
        );
    }

    /**
     * Compatibility path for existing controllers. It intentionally bypasses the local cache.
     * Identity therefore revalidates account, membership and current authorization versions on
     * every mint request. This is safe but less efficient than {@link #tokenFor(String, Jwt)}.
     */
    public String tokenFor(String audience, String tenantId, String actorId) {
        return mint(
                required(audience, "audience"),
                required(tenantId, "tenantId"),
                required(actorId, "actorId")
        ).accessToken();
    }

    /** Version-aware cached delegated flow. */
    public String tokenFor(
            String audience,
            String tenantId,
            String actorId,
            long authorizationVersion,
            long tenantAuthorizationVersion
    ) {
        Key key = new Key(
                audience,
                tenantId,
                actorId,
                authorizationVersion,
                tenantAuthorizationVersion
        );
        requireFresh(key);

        Cached current = cache.get(key);
        if (valid(current)) {
            return current.token();
        }

        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            try {
                requireFresh(key);
                current = cache.get(key);
                if (valid(current)) {
                    return current.token();
                }

                Grant grant = mint(key.audience(), key.tenantId(), key.actorId());
                requireFresh(key);
                long life = Math.max(2L, grant.expiresInSeconds());
                cache.put(
                        key,
                        new Cached(
                                grant.accessToken(),
                                Instant.now().plusSeconds(Math.max(1L, life * 3L / 4L))
                        )
                );
                return grant.accessToken();
            } finally {
                locks.remove(key, lock);
            }
        }
    }

    private Grant mint(String audience, String tenantId, String actorId) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "gateway service-client secret is not configured"
            );
        }
        if (identityInternalApiSecret.isBlank()) {
            throw new IllegalStateException(
                    "gateway identity internal API secret is not configured"
            );
        }
        Grant grant = identity.post()
                .uri("/internal/service-token")
                .header("X-Identity-Internal-Secret", identityInternalApiSecret)
                .bodyValue(new TokenRequest(
                        clientId,
                        secret,
                        audience,
                        tenantId,
                        actorId
                ))
                .retrieve()
                .bodyToMono(Grant.class)
                .block(requestTimeout);
        if (grant == null || grant.accessToken() == null
                || grant.accessToken().isBlank()) {
            throw new IllegalStateException("identity returned no service token");
        }
        return grant;
    }

    private void requireFresh(Key key) {
        if (authorizationWatermarks.isStale(
                key.tenantId(),
                key.actorId(),
                key.tenantAuthorizationVersion(),
                key.authorizationVersion()
        )) {
            invalidate(key.audience(), key.tenantId(), key.actorId());
            throw new AccessDeniedException(
                    "Authenticated user authorization is stale; refresh the access token"
            );
        }
    }

    public void invalidate(String audience, String tenantId) {
        String normalizedAudience = required(audience, "audience");
        String normalizedTenant = required(tenantId, "tenantId");
        cache.keySet().removeIf(key ->
                key.audience().equals(normalizedAudience)
                        && key.tenantId().equals(normalizedTenant)
        );
        locks.keySet().removeIf(key ->
                key.audience().equals(normalizedAudience)
                        && key.tenantId().equals(normalizedTenant)
        );
    }

    public void invalidate(String audience, String tenantId, String actorId) {
        String normalizedAudience = required(audience, "audience");
        String normalizedTenant = required(tenantId, "tenantId");
        String normalizedActor = required(actorId, "actorId");
        cache.keySet().removeIf(key ->
                key.audience().equals(normalizedAudience)
                        && key.tenantId().equals(normalizedTenant)
                        && key.actorId().equals(normalizedActor)
        );
        locks.keySet().removeIf(key ->
                key.audience().equals(normalizedAudience)
                        && key.tenantId().equals(normalizedTenant)
                        && key.actorId().equals(normalizedActor)
        );
    }

    public void invalidate(String audience) {
        String normalizedAudience = required(audience, "audience");
        cache.keySet().removeIf(key ->
                key.audience().equals(normalizedAudience)
        );
        locks.keySet().removeIf(key ->
                key.audience().equals(normalizedAudience)
        );
    }

    private static long requiredLongClaim(Jwt jwt, String claim) {
        Object value = jwt.getClaims().get(claim);
        if (!(value instanceof Number number)) {
            throw new AccessDeniedException(
                    "Authenticated user token is missing " + claim
            );
        }
        long result = number.longValue();
        nonNegative(result, claim);
        return result;
    }

    private static boolean valid(Cached value) {
        return value != null && Instant.now().isBefore(value.refreshAfter());
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static void nonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
