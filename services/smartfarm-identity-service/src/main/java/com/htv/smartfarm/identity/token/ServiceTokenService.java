package com.htv.smartfarm.identity.token;

import java.util.List;

import com.htv.smartfarm.identity.config.ServiceTokenSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ServiceTokenService {

    private static final Logger AUDIT = LoggerFactory.getLogger(
            "smartfarm.audit.service-token"
    );

    private final ServiceTokenSettings settings;
    private final TokenService tokens;
    private final PasswordEncoder encoder;

    public ServiceTokenService(
            ServiceTokenSettings settings,
            TokenService tokens,
            PasswordEncoder encoder
    ) {
        this.settings = settings;
        this.tokens = tokens;
        this.encoder = encoder;
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
            AUDIT.warn(
                    "service_token_denied reason=bad_credentials client_id={} audience={} tenant={} actor_id={}",
                    clientId, audience, tenantId, safe(actorId)
            );
            throw new SecurityException("invalid service client credentials");
        }
        if (!client.mayTarget(audience)) {
            AUDIT.warn(
                    "service_token_denied reason=audience_not_allowed client_id={} audience={} tenant={} actor_id={}",
                    clientId, audience, tenantId, safe(actorId)
            );
            throw new SecurityException("service audience is not allowed");
        }
        List<String> scopes = client.scopesFor(audience);
        String token = tokens.issueServiceToken(
                clientId,
                tenantId,
                audience,
                scopes,
                settings.ttl()
        );
        AUDIT.info(
                "service_token_granted client_id={} audience={} tenant={} actor_id={} scopes={} ttl_s={}",
                clientId, audience, tenantId, safe(actorId), scopes,
                settings.ttl().toSeconds()
        );
        return new Grant(token, "Bearer", settings.ttl().toSeconds(), audience);
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
