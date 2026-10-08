package com.htv.smartfarm.identity.grpc.security;

import com.htv.smartfarm.proto.common.v1.RequestContext;

import io.grpc.Status;

import org.springframework.stereotype.Component;

/**
 * Verifies protobuf request metadata against the authenticated gRPC caller.
 *
 * <p>A user token acts as itself. A service token acts as its machine subject by default, but a
 * trusted service carrying {@code identity:principal:impersonate} may propagate the original human
 * actor in {@link RequestContext#getActorId()}. The verified actor, never the raw body value, is
 * returned to application services and audit/event code.
 */
@Component
public class GrpcRequestSecurity {

    public static final String PRINCIPAL_READ = IdentityGrpcAuthorities.PRINCIPAL_READ;
    public static final String PRINCIPAL_UPDATE = IdentityGrpcAuthorities.PRINCIPAL_UPDATE;
    public static final String PERMISSION_CHECK = IdentityGrpcAuthorities.PERMISSION_CHECK;
    public static final String PRINCIPAL_IMPERSONATE = IdentityGrpcAuthorities.PRINCIPAL_IMPERSONATE;
    public static final String CROSS_TENANT = IdentityGrpcAuthorities.CROSS_TENANT;

    private final GrpcCallerProvider callerProvider;

    public GrpcRequestSecurity(GrpcCallerProvider callerProvider) {
        this.callerProvider = callerProvider;
    }

    public SecuredRequest authorizePrincipalRead(RequestContext context, String subjectId) {
        return authorizeSubjectRequest(context, subjectId, PRINCIPAL_READ,
                "Caller cannot read another principal");
    }

    public SecuredRequest authorizeProfileUpdate(RequestContext context, String subjectId) {
        return authorizeSubjectRequest(context, subjectId, PRINCIPAL_UPDATE,
                "Caller cannot update another principal");
    }

    public SecuredRequest authorizePermissionCheck(RequestContext context, String subjectId) {
        return authorizeSubjectRequest(context, subjectId, PERMISSION_CHECK,
                "Caller cannot check permissions for another principal");
    }

    public GrpcCaller authenticatedCaller() {
        return callerProvider.currentCaller();
    }

    public SecuredRequest authorizeCredentialSelf(
            RequestContext context,
            String requestedSubjectId,
            String requiredAuthority
    ) {
        requireContext(context);
        GrpcCaller caller = callerProvider.currentCaller();
        requireAuthority(caller, requiredAuthority);
        String tenantId = resolveTenant(caller, context);
        String actorId = resolveVerifiedActor(caller, context);
        String subjectId = normalize(requestedSubjectId);
        if (subjectId == null) subjectId = actorId;
        if (!subjectId.equals(actorId)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Credential self-service cannot target another principal")
                    .asRuntimeException();
        }
        validateCorrelation(caller, context);
        return new SecuredRequest(caller, tenantId, subjectId, actorId);
    }

    private SecuredRequest authorizeSubjectRequest(
            RequestContext context,
            String requestedSubjectId,
            String requiredAuthority,
            String crossSubjectError
    ) {
        requireContext(context);
        GrpcCaller caller = callerProvider.currentCaller();
        requireAuthority(caller, requiredAuthority);
        String tenantId = resolveTenant(caller, context);
        String actorId = resolveVerifiedActor(caller, context);
        String subjectId = normalize(requestedSubjectId);
        if (subjectId == null) subjectId = actorId;

        // A normal user may only target itself. A trusted service may target another principal
        // only when its service token explicitly carries the impersonation authority.
        if (!subjectId.equals(actorId)
                && !caller.hasAuthorityOrSuperAdmin(PRINCIPAL_IMPERSONATE)) {
            throw Status.PERMISSION_DENIED.withDescription(crossSubjectError).asRuntimeException();
        }
        validateCorrelation(caller, context);
        return new SecuredRequest(caller, tenantId, subjectId, actorId);
    }

    public SecuredAdminRequest authorizeTenantAdministration(
            RequestContext context,
            String requiredAuthority
    ) {
        requireContext(context);
        GrpcCaller caller = callerProvider.currentCaller();
        requireAuthority(caller, requiredAuthority);
        String tenantId = resolveTenant(caller, context);
        String actorId = resolveVerifiedActor(caller, context);
        validateCorrelation(caller, context);
        return new SecuredAdminRequest(caller, tenantId, actorId);
    }

    public SecuredPlatformRequest authorizePlatformAdministration(
            RequestContext context,
            String requiredAuthority
    ) {
        requireContext(context);
        GrpcCaller caller = callerProvider.currentCaller();
        requireAuthority(caller, requiredAuthority);
        String actorId = resolveVerifiedActor(caller, context);
        validateCorrelation(caller, context);
        return new SecuredPlatformRequest(caller, actorId);
    }

    private String resolveTenant(GrpcCaller caller, RequestContext context) {
        String authenticatedTenant = normalize(caller.tenantId());
        if (authenticatedTenant == null) {
            throw Status.UNAUTHENTICATED.withDescription("Authenticated tenant is missing")
                    .asRuntimeException();
        }
        String requestedTenant = normalize(context.getTenantId());
        if (requestedTenant == null || requestedTenant.equals(authenticatedTenant)) {
            return authenticatedTenant;
        }
        if (caller.hasAuthorityOrSuperAdmin(CROSS_TENANT)) return requestedTenant;
        throw Status.PERMISSION_DENIED
                .withDescription("context.tenant_id does not match authenticated tenant")
                .asRuntimeException();
    }

    private String resolveVerifiedActor(GrpcCaller caller, RequestContext context) {
        String authenticatedSubject = normalize(caller.subjectId());
        if (authenticatedSubject == null) {
            throw Status.UNAUTHENTICATED.withDescription("Authenticated subject is missing")
                    .asRuntimeException();
        }
        String requestedActor = normalize(context.getActorId());
        if (requestedActor == null || requestedActor.equals(authenticatedSubject)) {
            return authenticatedSubject;
        }
        if (caller.isService() && caller.hasAuthority(PRINCIPAL_IMPERSONATE)) {
            return requestedActor;
        }
        throw Status.PERMISSION_DENIED
                .withDescription("context.actor_id is not authorized for authenticated caller")
                .asRuntimeException();
    }

    private void validateCorrelation(GrpcCaller caller, RequestContext context) {
        String requestCorrelation = normalize(context.getCorrelationId());
        if (requestCorrelation == null) return;
        if (requestCorrelation.length() > 128) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("context.correlation_id must not exceed 128 characters")
                    .asRuntimeException();
        }
        String headerCorrelation = normalize(caller.correlationId());
        if (headerCorrelation != null && !headerCorrelation.equals(requestCorrelation)) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("context.correlation_id does not match x-correlation-id")
                    .asRuntimeException();
        }
    }

    private void requireAuthority(GrpcCaller caller, String authority) {
        if (!caller.hasAuthorityOrSuperAdmin(authority)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Missing required authority: " + authority)
                    .asRuntimeException();
        }
    }

    private static void requireContext(RequestContext context) {
        if (context == null) {
            throw Status.INVALID_ARGUMENT.withDescription("request context is required")
                    .asRuntimeException();
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record SecuredRequest(
            GrpcCaller caller,
            String tenantId,
            String subjectId,
            String actorId
    ) { }

    public record SecuredAdminRequest(
            GrpcCaller caller,
            String tenantId,
            String actorId
    ) { }

    public record SecuredPlatformRequest(
            GrpcCaller caller,
            String actorId
    ) { }
}
