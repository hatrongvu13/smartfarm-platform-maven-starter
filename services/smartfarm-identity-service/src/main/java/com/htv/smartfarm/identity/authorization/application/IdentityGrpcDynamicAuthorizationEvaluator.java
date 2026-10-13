package com.htv.smartfarm.identity.authorization.application;

import com.htv.smartfarm.identity.authorization.application.model.PermissionTarget;
import com.htv.smartfarm.security.core.SecurityIdentity;
import com.htv.smartfarm.security.grpc.dynamic.GrpcAuthorizationDecision;
import com.htv.smartfarm.security.grpc.dynamic.GrpcDynamicAuthorizationEvaluator;

import java.util.Locale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Identity-side live authorization evaluator used in Phase 3C SHADOW mode.
 *
 * <p>Dynamic authorization is available only for service identities carrying
 * a verified delegated actor. The required gRPC authority is converted into a
 * permission target and evaluated against the delegated actor's current
 * effective permissions.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityGrpcDynamicAuthorizationEvaluator
        implements GrpcDynamicAuthorizationEvaluator {

    private static final String SCOPE_PREFIX = "SCOPE_";

    private static final String SIGNED_DELEGATED_ACTOR_REQUIRED =
            "SIGNED_DELEGATED_ACTOR_REQUIRED";

    private static final String AUTHORITY_NOT_MAPPABLE =
            "AUTHORITY_NOT_MAPPABLE";

    private static final String FARM_RESOURCE_TYPE = "farm";

    private final AuthorizationService authorization;

    @Override
    public GrpcAuthorizationDecision evaluate(
            String fullMethodName,
            String requiredAuthority,
            SecurityIdentity identity
    ) {
        log.debug(
                "Evaluating dynamic gRPC authorization: "
                        + "method={}, requiredAuthority={}, tenantId={}, "
                        + "subjectId={}, service={}, hasDelegatedActor={}",
                fullMethodName,
                requiredAuthority,
                identity.tenantId(),
                identity.subject(),
                identity.isService(),
                identity.delegatedActor() != null
        );

        if (!identity.isService()
                || identity.delegatedActor() == null) {
            log.debug(
                    "Dynamic gRPC authorization unavailable because a signed "
                            + "delegated actor is required: method={}, "
                            + "requiredAuthority={}, subjectId={}, service={}",
                    fullMethodName,
                    requiredAuthority,
                    identity.subject(),
                    identity.isService()
            );

            return GrpcAuthorizationDecision.unavailable(
                    SIGNED_DELEGATED_ACTOR_REQUIRED
            );
        }

        PermissionTarget permissionTarget =
                target(requiredAuthority);

        if (permissionTarget == null) {
            log.debug(
                    "Dynamic gRPC authorization unavailable because the "
                            + "authority cannot be mapped: method={}, "
                            + "requiredAuthority={}",
                    fullMethodName,
                    requiredAuthority
            );

            return GrpcAuthorizationDecision.unavailable(
                    AUTHORITY_NOT_MAPPABLE
            );
        }

        var permissionDecision = authorization.checkPermission(
                identity.tenantId(),
                identity.effectiveActorSubject(),
                permissionTarget
        );

        if (permissionDecision.allowed()) {
            log.debug(
                    "Dynamic gRPC authorization allowed: method={}, "
                            + "requiredAuthority={}, tenantId={}, actorId={}, "
                            + "resourceType={}, action={}, reasonCode={}",
                    fullMethodName,
                    requiredAuthority,
                    identity.tenantId(),
                    identity.effectiveActorSubject(),
                    permissionTarget.resourceType(),
                    permissionTarget.action(),
                    permissionDecision.reasonCode()
            );

            return GrpcAuthorizationDecision.allow(
                    permissionDecision.reasonCode()
            );
        }

        log.debug(
                "Dynamic gRPC authorization denied: method={}, "
                        + "requiredAuthority={}, tenantId={}, actorId={}, "
                        + "resourceType={}, action={}, reasonCode={}",
                fullMethodName,
                requiredAuthority,
                identity.tenantId(),
                identity.effectiveActorSubject(),
                permissionTarget.resourceType(),
                permissionTarget.action(),
                permissionDecision.reasonCode()
        );

        return GrpcAuthorizationDecision.deny(
                permissionDecision.reasonCode()
        );
    }

    private PermissionTarget target(String authority) {
        if (authority == null
                || !authority.startsWith(SCOPE_PREFIX)) {
            log.debug(
                    "Authority cannot be mapped because it does not use the "
                            + "expected scope prefix: authority={}",
                    authority
            );

            return null;
        }

        String permissionCode = authority
                .substring(SCOPE_PREFIX.length())
                .trim()
                .toLowerCase(Locale.ROOT);

        int separator = permissionCode.lastIndexOf(':');

        if (separator <= 0
                || separator == permissionCode.length() - 1) {
            log.debug(
                    "Authority cannot be mapped because its permission code "
                            + "format is invalid: authority={}, permissionCode={}",
                    authority,
                    permissionCode
            );

            return null;
        }

        String resourceType = permissionCode
                .substring(0, separator)
                .replace(':', '-');

        String action = permissionCode.substring(separator + 1);

        if (FARM_RESOURCE_TYPE.equals(resourceType)) {
            // The exact farm ID comes from the request body and must be
            // evaluated later in the application layer.
            log.debug(
                    "Authority mapping deferred to the application layer "
                            + "because the farm resource requires a concrete "
                            + "resource ID: authority={}, action={}",
                    authority,
                    action
            );

            return null;
        }

        PermissionTarget permissionTarget = new PermissionTarget(
                resourceType,
                null,
                action
        );

        log.debug(
                "Authority mapped to permission target: authority={}, "
                        + "resourceType={}, action={}",
                authority,
                resourceType,
                action
        );

        return permissionTarget;
    }
}