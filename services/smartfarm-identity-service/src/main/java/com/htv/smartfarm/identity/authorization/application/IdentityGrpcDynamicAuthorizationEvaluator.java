package com.htv.smartfarm.identity.authorization.application;

import java.util.Locale;

import com.htv.smartfarm.identity.authorization.application.model.PermissionTarget;
import com.htv.smartfarm.security.core.SecurityIdentity;
import com.htv.smartfarm.security.grpc.dynamic.GrpcAuthorizationDecision;
import com.htv.smartfarm.security.grpc.dynamic.GrpcDynamicAuthorizationEvaluator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Identity-side live evaluator used in Phase 3C SHADOW mode. */
@Component
@RequiredArgsConstructor
public class IdentityGrpcDynamicAuthorizationEvaluator
        implements GrpcDynamicAuthorizationEvaluator {

    private static final String SCOPE_PREFIX = "SCOPE_";
    private final AuthorizationService authorization;

    @Override
    public GrpcAuthorizationDecision evaluate(
            String fullMethodName,
            String requiredAuthority,
            SecurityIdentity identity
    ) {
        if (!identity.isService() || identity.delegatedActor() == null) {
            return GrpcAuthorizationDecision.unavailable(
                    "SIGNED_DELEGATED_ACTOR_REQUIRED"
            );
        }
        PermissionTarget target = target(requiredAuthority);
        if (target == null) {
            return GrpcAuthorizationDecision.unavailable(
                    "AUTHORITY_NOT_MAPPABLE"
            );
        }
        var decision = authorization.checkPermission(
                identity.tenantId(),
                identity.effectiveActorSubject(),
                target
        );
        return decision.allowed()
                ? GrpcAuthorizationDecision.allow(decision.reasonCode())
                : GrpcAuthorizationDecision.deny(decision.reasonCode());
    }

    private PermissionTarget target(String authority) {
        if (authority == null || !authority.startsWith(SCOPE_PREFIX)) {
            return null;
        }
        String code = authority.substring(SCOPE_PREFIX.length())
                .trim()
                .toLowerCase(Locale.ROOT);
        int separator = code.lastIndexOf(':');
        if (separator <= 0 || separator == code.length() - 1) {
            return null;
        }
        String resourceType = code.substring(0, separator)
                .replace(':', '-');
        String action = code.substring(separator + 1);
        if ("farm".equals(resourceType)) {
            // Exact farm ID is request-body data and is evaluated in the application layer.
            return null;
        }
        return new PermissionTarget(resourceType, null, action);
    }
}
