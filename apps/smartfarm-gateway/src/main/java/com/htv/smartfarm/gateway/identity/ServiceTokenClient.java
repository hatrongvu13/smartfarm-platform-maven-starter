package com.htv.smartfarm.gateway.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.identity.v1.ExchangeDelegatedTokenRequest;
import com.htv.smartfarm.proto.identity.v1.IdentityTokenServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.TokenGrantResponse;
import com.htv.smartfarm.security.authorization.AuthorizationVersionWatermarkStore;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;

import io.grpc.StatusRuntimeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Gateway client for delegated service tokens issued by Identity over gRPC.
 */
@Component
public class ServiceTokenClient {
    private final IdentityTokenServiceGrpc.IdentityTokenServiceBlockingStub identity;
    private final String clientId;
    private final String secret;
    private final Duration requestTimeout;
    private final AuthorizationVersionWatermarkStore authorizationWatermarks;
    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Key, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public ServiceTokenClient(
            IdentityTokenServiceGrpc.IdentityTokenServiceBlockingStub identity,
            @Value("${smartfarm.gateway.service-client.id:gateway}") String clientId,
            @Value("${smartfarm.gateway.service-client.secret:}") String secret,
            @Value("${smartfarm.gateway.grpc.service-token-timeout:5s}") Duration requestTimeout,
            AuthorizationVersionWatermarkStore authorizationWatermarks
    ) {
        this.identity = java.util.Objects.requireNonNull(identity, "identity");
        this.clientId = required(clientId, "clientId");
        this.secret = secret;
        this.requestTimeout = positive(requestTimeout, "service token timeout");
        this.authorizationWatermarks = java.util.Objects.requireNonNull(
                authorizationWatermarks,
                "authorizationWatermarks"
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

    /** Actor, tenant, and authorization versions are taken only from the verified user JWT. */
    public String tokenFor(String audience, Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("jwt must not be null");
        }
        Key key = new Key(
                audience,
                required(jwt.getClaimAsString("tenant_id"), "tenantId"),
                required(jwt.getSubject(), "actorId"),
                requiredLongClaim(jwt, "authorization_version"),
                requiredLongClaim(jwt, "tenant_authorization_version")
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

                TokenGrantResponse grant = mint(key, jwt);
                requireFresh(key);
                long life = Math.max(2L, grant.getExpiresInSeconds());
                cache.put(
                        key,
                        new Cached(
                                grant.getAccessToken(),
                                Instant.now().plusSeconds(Math.max(1L, life * 3L / 4L))
                        )
                );
                return grant.getAccessToken();
            } finally {
                locks.remove(key, lock);
            }
        }
    }

    private TokenGrantResponse mint(Key key, Jwt jwt) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("gateway service-client secret is not configured");
        }
        String correlationId = UUID.randomUUID().toString();
        RequestContext context = RequestContext.newBuilder()
                .setTenantId(key.tenantId())
                .setActorId(key.actorId())
                .setCorrelationId(correlationId)
                .build();
        try {
            TokenGrantResponse grant = identity
                    .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .withCallCredentials(new BearerCallCredentials(
                            jwt::getTokenValue,
                            key::tenantId,
                            () -> correlationId
                    ))
                    .exchangeDelegatedToken(
                            ExchangeDelegatedTokenRequest.newBuilder()
                                    .setContext(context)
                                    .setClientId(clientId)
                                    .setClientSecret(secret)
                                    .setAudience(key.audience())
                                    .setActorSubjectId(key.actorId())
                                    .build()
                    );
            if (grant.getAccessToken().isBlank()
                    || !key.audience().equals(grant.getAudience())) {
                throw new IllegalStateException("identity returned an invalid delegated token grant");
            }
            return grant;
        } catch (StatusRuntimeException failure) {
            throw new IllegalStateException(
                    "identity delegated token gRPC request failed: "
                            + failure.getStatus().getCode(),
                    failure
            );
        }
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
        cache.keySet().removeIf(key -> key.audience().equals(normalizedAudience));
        locks.keySet().removeIf(key -> key.audience().equals(normalizedAudience));
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
