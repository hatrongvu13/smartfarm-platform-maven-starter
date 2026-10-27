package com.htv.farmverse.gateway.identity;

import java.util.UUID;

import com.htv.farmverse.proto.common.v1.RequestContext;

import com.htv.farmverse.common.contract.TokenClaimContract;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class GatewayRequestContextFactory {
    public RequestContext create(Jwt jwt) {
        return create(jwt, null, null);
    }

    public RequestContext create(Jwt jwt, String idempotencyKey) {
        return create(jwt, null, idempotencyKey);
    }

    public RequestContext create(Jwt jwt, String correlationId, String idempotencyKey) {
        String tenant = jwt.getClaimAsString(TokenClaimContract.TENANT_ID);
        String actor = jwt.getSubject();
        if (tenant == null || tenant.isBlank() || actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("authenticated identity is incomplete");
        }
        String correlation = normalize(correlationId);
        if (correlation == null || correlation.length() > 128) {
            correlation = UUID.randomUUID().toString();
        }
        RequestContext.Builder result = RequestContext.newBuilder()
                .setTenantId(tenant.trim())
                .setActorId(actor.trim())
                .setCorrelationId(correlation);
        String idempotency = normalize(idempotencyKey);
        if (idempotency != null) result.setIdempotencyKey(idempotency);
        return result.build();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
