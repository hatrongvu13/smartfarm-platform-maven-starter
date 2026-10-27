package com.htv.farmverse.identity.token;

import java.util.List;

import com.htv.farmverse.identity.config.ServiceTokenSettings;
import com.htv.farmverse.identity.token.grant.GrantType;
import com.htv.farmverse.identity.token.grant.TokenContext;
import com.htv.farmverse.identity.token.grant.TokenGrantFacade;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ServiceTokenService {

    private final ServiceTokenSettings settings;
    private final TokenGrantFacade tokenGrants;
    private final PasswordEncoder encoder;
    private final DelegatedActorVerificationService delegatedActors;

    public ServiceTokenService(
            ServiceTokenSettings settings,
            TokenGrantFacade tokenGrants,
            PasswordEncoder encoder,
            DelegatedActorVerificationService delegatedActors
    ) {
        this.settings = settings;
        this.tokenGrants = tokenGrants;
        this.encoder = encoder;
        this.delegatedActors = delegatedActors;
    }

    public record Grant(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            String audience
    ) {
    }

    public Grant issue(
            String clientId,
            String secret,
            String audience,
            String tenantId,
            String actorId
    ) {
        requireText(clientId, "client_id");
        requireText(audience, "audience");
        requireText(tenantId, "tenant_id");
        ServiceTokenSettings.Client client = settings.clients().get(clientId);
        if (client == null || secret == null
                || !encoder.matches(secret, client.secret())) {
            log.warn(
                    "service_token_denied reason=bad_credentials client_id={} audience={} tenant={} actor_id={}",
                    clientId, audience, tenantId, safe(actorId)
            );
            throw new SecurityException("invalid service client credentials");
        }
        if (!client.mayTarget(audience)) {
            log.warn(
                    "service_token_denied reason=audience_not_allowed client_id={} audience={} tenant={} actor_id={}",
                    clientId, audience, tenantId, safe(actorId)
            );
            throw new SecurityException("service audience is not allowed");
        }
        List<String> scopes = client.scopesFor(audience);
        String normalizedActor = actorId == null || actorId.isBlank()
                ? null
                : actorId.trim();
        if (normalizedActor != null && !client.delegationEnabled()) {
            log.warn(
                    "service_token_denied reason=delegation_not_allowed client_id={} audience={} tenant={} actor_id={}",
                    clientId, audience, tenantId, safe(normalizedActor)
            );
            throw new SecurityException(
                    "service client is not allowed to delegate an actor"
            );
        }
        var grantType = normalizedActor == null
                ? GrantType.SERVICE_CLIENT
                : GrantType.DELEGATED_SERVICE;
        long subjectVersion = 0;
        long tenantVersion = 0;
        if (normalizedActor != null) {
            var versions = delegatedActors.verify(tenantId, normalizedActor);
            subjectVersion = versions.subjectVersion();
            tenantVersion = versions.tenantVersion();
        }
        var context = new TokenContext(
                grantType,
                "svc:" + clientId,
                tenantId,
                audience,
                clientId,
                normalizedActor,
                new java.util.LinkedHashSet<>(scopes),
                java.util.Set.of(),
                subjectVersion,
                tenantVersion,
                null,
                java.time.Instant.now(),
                java.util.List.of("client_secret"),
                settings.ttl()
        );
        String token = tokenGrants.issue(context).accessToken().value();
        long ttlSeconds = settings.ttl().toSeconds();
        log.info(
                "service_token_granted client_id={} audience={} tenant={} actor_id={} scopes={} ttl_s={}",
                clientId, audience, tenantId, safe(actorId), scopes,
                ttlSeconds
        );
        return new Grant(token, "Bearer", ttlSeconds, audience);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
