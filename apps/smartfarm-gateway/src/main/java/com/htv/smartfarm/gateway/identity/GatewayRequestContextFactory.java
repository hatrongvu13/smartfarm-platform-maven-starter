package com.htv.smartfarm.gateway.identity;

import java.util.UUID;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class GatewayRequestContextFactory {
    public RequestContext create(Jwt jwt) {
        String tenant = jwt.getClaimAsString("tenant_id");
        if (tenant == null || tenant.isBlank() || jwt.getSubject() == null || jwt.getSubject().isBlank())
            throw new IllegalArgumentException("authenticated identity is incomplete");
        return RequestContext.newBuilder().setTenantId(tenant).setActorId(jwt.getSubject())
                .setCorrelationId(UUID.randomUUID().toString()).build();
    }
}
