package com.htv.smartfarm.reporting.grpc;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.identity.v1.IdentityTokenServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.IssueServiceTokenRequest;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Fetches machine-to-machine tokens from Identity over the internal gRPC API. */
@Component
public class ServiceTokenClient {
    private static final Logger log = LoggerFactory.getLogger(ServiceTokenClient.class);

    private final IdentityTokenServiceGrpc.IdentityTokenServiceBlockingStub identity;
    private final String clientId;
    private final String secret;
    private final long timeoutMillis;
    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();

    public ServiceTokenClient(
            IdentityTokenServiceGrpc.IdentityTokenServiceBlockingStub identity,
            @Value("${smartfarm.reporting.service-client.id:reporting}") String clientId,
            @Value("${smartfarm.reporting.service-client.secret:}") String secret,
            @Value("${smartfarm.identity.grpc.token-timeout:5s}") java.time.Duration timeout
    ) {
        this.identity = java.util.Objects.requireNonNull(identity, "identity");
        this.clientId = required(clientId, "clientId");
        this.secret = secret;
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("identity gRPC token timeout must be positive");
        }
        this.timeoutMillis = timeout.toMillis();
    }

    private record Key(String audience, String tenantId, String actorId) {
        private Key {
            audience = required(audience, "audience");
            tenantId = required(tenantId, "tenantId");
            actorId = actorId == null || actorId.isBlank() ? "" : actorId.trim();
        }
    }

    private record Cached(String token, Instant refreshAfter) {
    }

    public String tokenFor(String audience, String tenantId, String actorId) {
        Key key = new Key(audience, tenantId, actorId);
        Cached current = cache.get(key);
        if (valid(current)) {
            return current.token();
        }
        synchronized (this) {
            current = cache.get(key);
            if (valid(current)) {
                return current.token();
            }
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException("reporting service-client secret is not configured");
            }
            String correlationId = java.util.UUID.randomUUID().toString();
            RequestContext context = RequestContext.newBuilder()
                    .setTenantId(key.tenantId())
                    .setActorId(key.actorId())
                    .setCorrelationId(correlationId)
                    .build();
            try {
                var grant = identity.withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS)
                        .issueServiceToken(IssueServiceTokenRequest.newBuilder()
                                .setContext(context)
                                .setClientId(clientId)
                                .setClientSecret(secret)
                                .setAudience(key.audience())
                                .build());
                if (grant.getAccessToken().isBlank()
                        || !key.audience().equals(grant.getAudience())) {
                    throw new IllegalStateException("identity returned an invalid service token grant");
                }
                long lifetime = Math.max(5L, grant.getExpiresInSeconds());
                cache.put(key, new Cached(
                        grant.getAccessToken(),
                        Instant.now().plusSeconds(Math.max(1L, lifetime * 3L / 4L))
                ));
                log.debug("Fetched service token audience={} tenant={} expires_in_s={}",
                        key.audience(), key.tenantId(), grant.getExpiresInSeconds());
                return grant.getAccessToken();
            } catch (StatusRuntimeException failure) {
                throw new IllegalStateException(
                        "identity service-token gRPC request failed: "
                                + failure.getStatus().getCode(),
                        failure
                );
            }
        }
    }

    public String tokenForMachine(String audience, String tenantId) {
        return tokenFor(audience, tenantId, null);
    }

    public void invalidate(String audience) {
        cache.keySet().removeIf(key -> key.audience().equals(audience));
    }

    private static boolean valid(Cached value) {
        return value != null && Instant.now().isBefore(value.refreshAfter());
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
