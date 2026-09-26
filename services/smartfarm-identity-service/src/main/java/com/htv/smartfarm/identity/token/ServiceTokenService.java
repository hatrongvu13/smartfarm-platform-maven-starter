package com.htv.smartfarm.identity.token;

import com.htv.smartfarm.identity.config.ServiceTokenSettings;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Issues machine-to-machine (service) tokens for the client-credentials style flow
 * (Hướng A). Every request is checked against the {@link ServiceTokenSettings} whitelist:
 * the caller must present a known {@code clientId} + secret, and may only obtain a token
 * for an audience it is registered to call, carrying at most the scopes granted for that
 * audience. Each grant (and each rejection) is audit-logged with the requesting client,
 * the target audience, the tenant and the human actor on whose behalf the call is made —
 * so a downstream action can always be traced back to the user who triggered it.
 */
@Service
public class ServiceTokenService {

    private static final Logger AUDIT = LoggerFactory.getLogger("smartfarm.audit.service-token");

    private final ServiceTokenSettings settings;
    private final TokenService tokens;
    private final PasswordEncoder encoder;

    public ServiceTokenService(ServiceTokenSettings settings, TokenService tokens, PasswordEncoder encoder) {
        this.settings = settings;
        this.tokens = tokens;
        this.encoder = encoder;
    }

    public record Grant(String accessToken, String tokenType, long expiresInSeconds, String audience) {
    }

    /**
     * @param clientId  caller identity (e.g. {@code gateway})
     * @param secret    caller's shared secret (matched against the stored bcrypt hash)
     * @param audience  downstream service audience the token is minted for
     * @param tenantId  tenant the call operates within
     * @param actorId   human actor on whose behalf the call is made (for audit only; not embedded in the token)
     */
    public Grant issue(String clientId, String secret, String audience, String tenantId, String actorId) {
        if (clientId == null || clientId.isBlank() || audience == null || audience.isBlank()
                || tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("client_id, audience and tenant required");

        ServiceTokenSettings.Client client = settings.clients().get(clientId);
        if (client == null || secret == null || !encoder.matches(secret, client.secret())) {
            AUDIT.warn("service_token_denied reason=bad_credentials client_id={} audience={} tenant={} actor_id={}",
                    clientId, audience, tenantId, safe(actorId));
            throw new SecurityException("invalid service client credentials");
        }
        if (!client.mayTarget(audience)) {
            AUDIT.warn("service_token_denied reason=audience_not_allowed client_id={} audience={} tenant={} actor_id={}",
                    clientId, audience, tenantId, safe(actorId));
            throw new SecurityException("client not allowed to target audience " + audience);
        }

        List<String> scopes = client.scopesFor(audience);
        String token = tokens.issueServiceToken(clientId, audience, tenantId, scopes, settings.ttl());
        AUDIT.info("service_token_granted client_id={} audience={} tenant={} actor_id={} scopes={} ttl_s={}",
                clientId, audience, tenantId, safe(actorId), scopes, settings.ttl().toSeconds());
        return new Grant(token, "Bearer", settings.ttl().toSeconds(), audience);
    }

    private static String safe(String actorId) {
        return actorId == null || actorId.isBlank() ? "-" : actorId;
    }
}
