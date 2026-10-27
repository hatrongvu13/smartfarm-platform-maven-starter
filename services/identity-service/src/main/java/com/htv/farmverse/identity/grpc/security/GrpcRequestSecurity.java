package com.htv.farmverse.identity.grpc.security;

import com.htv.farmverse.proto.common.v1.RequestContext;

import io.grpc.Status;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Verifies protobuf request metadata against the authenticated gRPC caller.
 *
 * <p>A user token acts as itself. A service token acts as its machine subject by default, but a
 * trusted service carrying {@code identity:principal:impersonate} may propagate the original human
 * actor in {@link RequestContext#getActorId()}. The verified actor, never the raw body value, is
 * returned to application services and audit/event code.
 */
@Slf4j
@Component
public class GrpcRequestSecurity {

    public static final String PRINCIPAL_READ =
            IdentityGrpcAuthorities.PRINCIPAL_READ;

    public static final String PRINCIPAL_UPDATE =
            IdentityGrpcAuthorities.PRINCIPAL_UPDATE;

    public static final String PERMISSION_CHECK =
            IdentityGrpcAuthorities.PERMISSION_CHECK;

    public static final String PRINCIPAL_IMPERSONATE =
            IdentityGrpcAuthorities.PRINCIPAL_IMPERSONATE;

    public static final String CROSS_TENANT =
            IdentityGrpcAuthorities.CROSS_TENANT;

    private final GrpcCallerProvider callerProvider;

    public GrpcRequestSecurity(GrpcCallerProvider callerProvider) {
        this.callerProvider = callerProvider;
    }

    public SecuredRequest authorizePrincipalRead(
            RequestContext context,
            String subjectId
    ) {
        log.debug(
                "Authorizing principal read: requestedSubjectId={}",
                subjectId
        );

        return authorizeSubjectRequest(
                context,
                subjectId,
                PRINCIPAL_READ,
                "Caller cannot read another principal"
        );
    }

    public SecuredRequest authorizeProfileUpdate(
            RequestContext context,
            String subjectId
    ) {
        log.debug(
                "Authorizing profile update: requestedSubjectId={}",
                subjectId
        );

        return authorizeSubjectRequest(
                context,
                subjectId,
                PRINCIPAL_UPDATE,
                "Caller cannot update another principal"
        );
    }

    public SecuredRequest authorizePermissionCheck(
            RequestContext context,
            String subjectId
    ) {
        log.debug(
                "Authorizing permission check: requestedSubjectId={}",
                subjectId
        );

        return authorizeSubjectRequest(
                context,
                subjectId,
                PERMISSION_CHECK,
                "Caller cannot check permissions for another principal"
        );
    }

    public void authenticatedCaller() {
        GrpcCaller caller = callerProvider.currentCaller();

        log.debug(
                "Authenticated gRPC caller resolved: subjectId={}, tenantId={}, service={}",
                caller.subjectId(),
                caller.tenantId(),
                caller.isService()
        );

    }

    public SecuredRequest authorizeCredentialSelf(
            RequestContext context,
            String requestedSubjectId,
            String requiredAuthority
    ) {
        requireContext(context);

        GrpcCaller caller = callerProvider.currentCaller();

        log.debug(
                "Authorizing credential self-service: callerSubjectId={}, "
                        + "callerTenantId={}, requestedSubjectId={}, requiredAuthority={}",
                caller.subjectId(),
                caller.tenantId(),
                requestedSubjectId,
                requiredAuthority
        );

        requireAuthority(caller, requiredAuthority);

        String tenantId = resolveTenant(caller, context);
        String actorId = resolveVerifiedActor(caller, context);
        String subjectId = normalize(requestedSubjectId);

        if (subjectId == null) {
            subjectId = actorId;

            log.debug(
                    "Credential subject defaulted to verified actor: actorId={}",
                    actorId
            );
        }

        if (!subjectId.equals(actorId)) {
            log.warn(
                    "Credential self-service denied because target differs from actor: "
                            + "tenantId={}, actorId={}, subjectId={}, requiredAuthority={}",
                    tenantId,
                    actorId,
                    subjectId,
                    requiredAuthority
            );

            throw Status.PERMISSION_DENIED
                    .withDescription(
                            "Credential self-service cannot target another principal"
                    )
                    .asRuntimeException();
        }

        validateCorrelation(caller, context);

        log.debug(
                "Credential self-service authorized: tenantId={}, actorId={}, "
                        + "subjectId={}, requiredAuthority={}",
                tenantId,
                actorId,
                subjectId,
                requiredAuthority
        );

        return new SecuredRequest(
                caller,
                tenantId,
                subjectId,
                actorId
        );
    }

    private SecuredRequest authorizeSubjectRequest(
            RequestContext context,
            String requestedSubjectId,
            String requiredAuthority,
            String crossSubjectError
    ) {
        requireContext(context);

        GrpcCaller caller = callerProvider.currentCaller();

        log.debug(
                "Authorizing subject request: callerSubjectId={}, callerTenantId={}, "
                        + "requestedSubjectId={}, requiredAuthority={}",
                caller.subjectId(),
                caller.tenantId(),
                requestedSubjectId,
                requiredAuthority
        );

        requireAuthority(caller, requiredAuthority);

        String tenantId = resolveTenant(caller, context);
        String actorId = resolveVerifiedActor(caller, context);
        String subjectId = normalize(requestedSubjectId);

        if (subjectId == null) {
            subjectId = actorId;

            log.debug(
                    "Requested subject defaulted to verified actor: actorId={}",
                    actorId
            );
        }

        // A normal user may only target itself. A trusted service may target another principal
        // only when its service token explicitly carries the impersonation authority.
        if (!subjectId.equals(actorId)
                && !caller.hasAuthorityOrSuperAdmin(PRINCIPAL_IMPERSONATE)) {
            log.warn(
                    "Cross-subject request denied: tenantId={}, actorId={}, "
                            + "subjectId={}, requiredAuthority={}",
                    tenantId,
                    actorId,
                    subjectId,
                    requiredAuthority
            );

            throw Status.PERMISSION_DENIED
                    .withDescription(crossSubjectError)
                    .asRuntimeException();
        }

        validateCorrelation(caller, context);

        log.debug(
                "Subject request authorized: tenantId={}, actorId={}, "
                        + "subjectId={}, requiredAuthority={}",
                tenantId,
                actorId,
                subjectId,
                requiredAuthority
        );

        return new SecuredRequest(
                caller,
                tenantId,
                subjectId,
                actorId
        );
    }

    public SecuredAdminRequest authorizeTenantAdministration(
            RequestContext context,
            String requiredAuthority
    ) {
        requireContext(context);

        GrpcCaller caller = callerProvider.currentCaller();

        log.debug(
                "Authorizing tenant administration: callerSubjectId={}, "
                        + "callerTenantId={}, requiredAuthority={}",
                caller.subjectId(),
                caller.tenantId(),
                requiredAuthority
        );

        requireAuthority(caller, requiredAuthority);

        String tenantId = resolveTenant(caller, context);
        String actorId = resolveVerifiedActor(caller, context);

        validateCorrelation(caller, context);

        log.debug(
                "Tenant administration authorized: tenantId={}, "
                        + "actorId={}, requiredAuthority={}",
                tenantId,
                actorId,
                requiredAuthority
        );

        return new SecuredAdminRequest(
                caller,
                tenantId,
                actorId
        );
    }

    public SecuredPlatformRequest authorizePlatformAdministration(
            RequestContext context,
            String requiredAuthority
    ) {
        requireContext(context);

        GrpcCaller caller = callerProvider.currentCaller();

        log.debug(
                "Authorizing platform administration: callerSubjectId={}, "
                        + "callerTenantId={}, requiredAuthority={}",
                caller.subjectId(),
                caller.tenantId(),
                requiredAuthority
        );

        requireAuthority(caller, requiredAuthority);

        String actorId = resolveVerifiedActor(caller, context);

        validateCorrelation(caller, context);

        log.debug(
                "Platform administration authorized: actorId={}, requiredAuthority={}",
                actorId,
                requiredAuthority
        );

        return new SecuredPlatformRequest(
                caller,
                actorId
        );
    }

    private String resolveTenant(
            GrpcCaller caller,
            RequestContext context
    ) {
        String authenticatedTenant = normalize(caller.tenantId());

        if (authenticatedTenant == null) {
            log.warn(
                    "Authenticated tenant is missing: callerSubjectId={}",
                    caller.subjectId()
            );

            throw Status.UNAUTHENTICATED
                    .withDescription("Authenticated tenant is missing")
                    .asRuntimeException();
        }

        String requestedTenant = normalize(context.getTenantId());

        if (requestedTenant == null
                || requestedTenant.equals(authenticatedTenant)) {
            log.debug(
                    "Tenant resolved using authenticated tenant: tenantId={}",
                    authenticatedTenant
            );

            return authenticatedTenant;
        }

        if (caller.hasAuthorityOrSuperAdmin(CROSS_TENANT)) {
            log.debug(
                    "Cross-tenant access authorized: authenticatedTenantId={}, "
                            + "requestedTenantId={}, callerSubjectId={}",
                    authenticatedTenant,
                    requestedTenant,
                    caller.subjectId()
            );

            return requestedTenant;
        }

        log.warn(
                "Cross-tenant access denied: authenticatedTenantId={}, "
                        + "requestedTenantId={}, callerSubjectId={}",
                authenticatedTenant,
                requestedTenant,
                caller.subjectId()
        );

        throw Status.PERMISSION_DENIED
                .withDescription(
                        "context.tenant_id does not match authenticated tenant"
                )
                .asRuntimeException();
    }

    private String resolveVerifiedActor(
            GrpcCaller caller,
            RequestContext context
    ) {
        String authenticatedSubject = normalize(caller.subjectId());

        if (authenticatedSubject == null) {
            log.warn(
                    "Authenticated subject is missing: callerTenantId={}",
                    caller.tenantId()
            );

            throw Status.UNAUTHENTICATED
                    .withDescription("Authenticated subject is missing")
                    .asRuntimeException();
        }

        String requestedActor = normalize(context.getActorId());

        if (requestedActor == null
                || requestedActor.equals(authenticatedSubject)) {
            log.debug(
                    "Actor resolved using authenticated subject: actorId={}",
                    authenticatedSubject
            );

            return authenticatedSubject;
        }

        if (caller.isService()
                && caller.hasAuthority(PRINCIPAL_IMPERSONATE)) {
            log.debug(
                    "Service actor impersonation authorized: "
                            + "authenticatedSubjectId={}, requestedActorId={}, tenantId={}",
                    authenticatedSubject,
                    requestedActor,
                    caller.tenantId()
            );

            return requestedActor;
        }

        log.warn(
                "Actor impersonation denied: authenticatedSubjectId={}, "
                        + "requestedActorId={}, tenantId={}, service={}",
                authenticatedSubject,
                requestedActor,
                caller.tenantId(),
                caller.isService()
        );

        throw Status.PERMISSION_DENIED
                .withDescription(
                        "context.actor_id is not authorized for authenticated caller"
                )
                .asRuntimeException();
    }

    private void validateCorrelation(
            GrpcCaller caller,
            RequestContext context
    ) {
        String requestCorrelation = normalize(context.getCorrelationId());

        if (requestCorrelation == null) {
            log.debug("Request correlation ID is not provided");
            return;
        }

        if (requestCorrelation.length() > 128) {
            log.warn(
                    "Request correlation ID exceeds maximum length: "
                            + "length={}, callerSubjectId={}, callerTenantId={}",
                    requestCorrelation.length(),
                    caller.subjectId(),
                    caller.tenantId()
            );

            throw Status.INVALID_ARGUMENT
                    .withDescription(
                            "context.correlation_id must not exceed 128 characters"
                    )
                    .asRuntimeException();
        }

        String headerCorrelation = normalize(caller.correlationId());

        if (headerCorrelation != null
                && !headerCorrelation.equals(requestCorrelation)) {
            log.warn(
                    "Correlation ID mismatch: headerCorrelationId={}, "
                            + "requestCorrelationId={}, callerSubjectId={}, callerTenantId={}",
                    headerCorrelation,
                    requestCorrelation,
                    caller.subjectId(),
                    caller.tenantId()
            );

            throw Status.INVALID_ARGUMENT
                    .withDescription(
                            "context.correlation_id does not match x-correlation-id"
                    )
                    .asRuntimeException();
        }

        log.debug(
                "Correlation ID validated: correlationId={}",
                requestCorrelation
        );
    }

    private void requireAuthority(
            GrpcCaller caller,
            String authority
    ) {
        if (!caller.hasAuthorityOrSuperAdmin(authority)) {
            log.warn(
                    "Required authority is missing: authority={}, "
                            + "callerSubjectId={}, callerTenantId={}, service={}",
                    authority,
                    caller.subjectId(),
                    caller.tenantId(),
                    caller.isService()
            );

            throw Status.PERMISSION_DENIED
                    .withDescription("Missing required authority: " + authority)
                    .asRuntimeException();
        }

        log.debug(
                "Required authority validated: authority={}, callerSubjectId={}",
                authority,
                caller.subjectId()
        );
    }

    private static void requireContext(RequestContext context) {
        if (context == null) {
            log.warn("Request context is missing");

            throw Status.INVALID_ARGUMENT
                    .withDescription("request context is required")
                    .asRuntimeException();
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    public record SecuredRequest(
            GrpcCaller caller,
            String tenantId,
            String subjectId,
            String actorId
    ) {
    }

    public record SecuredAdminRequest(
            GrpcCaller caller,
            String tenantId,
            String actorId
    ) {
    }

    public record SecuredPlatformRequest(
            GrpcCaller caller,
            String actorId
    ) {
    }
}