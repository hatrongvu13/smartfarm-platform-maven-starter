package com.htv.smartfarm.identity.grpc.security;

import com.htv.smartfarm.proto.common.v1.RequestContext;

import io.grpc.Status;

import org.springframework.stereotype.Component;

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

    public GrpcRequestSecurity(
            GrpcCallerProvider callerProvider
    ) {
        this.callerProvider = callerProvider;
    }

    public SecuredRequest authorizePrincipalRead(
            RequestContext requestContext,
            String requestedSubjectId
    ) {
        return authorizeSubjectRequest(
                requestContext,
                requestedSubjectId,
                PRINCIPAL_READ,
                "Caller cannot read another principal"
        );
    }

    public SecuredRequest authorizeProfileUpdate(
            RequestContext requestContext,
            String requestedSubjectId
    ) {
        return authorizeSubjectRequest(
                requestContext,
                requestedSubjectId,
                PRINCIPAL_UPDATE,
                "Caller cannot update another principal"
        );
    }

    public SecuredRequest authorizePermissionCheck(
            RequestContext requestContext,
            String requestedSubjectId
    ) {
        return authorizeSubjectRequest(
                requestContext,
                requestedSubjectId,
                PERMISSION_CHECK,
                "Caller cannot check permissions for another principal"
        );
    }

    public GrpcCaller authenticatedCaller() {
        return callerProvider.currentCaller();
    }

    public SecuredRequest authorizeCredentialSelf(
            RequestContext requestContext,
            String requestedSubjectId,
            String requiredAuthority
    ) {
        if (requestContext == null) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("request context is required")
                    .asRuntimeException();
        }

        GrpcCaller caller = callerProvider.currentCaller();
        requireAuthority(caller, requiredAuthority);
        String tenantId = resolveTenant(caller, requestContext);
        String actorId = normalize(requestContext.getActorId());

        if (actorId == null) {
            actorId = normalize(caller.subjectId());
        }

        if (actorId == null) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Authenticated actor is missing")
                    .asRuntimeException();
        }

        String subjectId = normalize(requestedSubjectId);
        if (subjectId == null) {
            subjectId = actorId;
        }

        if (!subjectId.equals(actorId)) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Credential self-service cannot target another principal")
                    .asRuntimeException();
        }

        validateCorrelation(caller, requestContext);
        return new SecuredRequest(caller, tenantId, subjectId);
    }

    private SecuredRequest authorizeSubjectRequest(
            RequestContext requestContext,
            String requestedSubjectId,
            String requiredAuthority,
            String crossSubjectError
    ) {
        if (requestContext == null) {
            throw Status.INVALID_ARGUMENT
                    .withDescription(
                            "request context is required"
                    )
                    .asRuntimeException();
        }

        GrpcCaller caller = callerProvider.currentCaller();

        requireAuthority(
                caller,
                requiredAuthority
        );

        String tenantId = resolveTenant(
                caller,
                requestContext
        );

        String subjectId = resolveSubject(
                caller,
                requestedSubjectId
        );

        if (!subjectId.equals(caller.subjectId())
                && !caller.hasAuthorityOrSuperAdmin(
                PRINCIPAL_IMPERSONATE
        )) {
            throw Status.PERMISSION_DENIED
                    .withDescription(crossSubjectError)
                    .asRuntimeException();
        }

        validateContextActor(
                caller,
                requestContext
        );

        validateCorrelation(
                caller,
                requestContext
        );

        return new SecuredRequest(
                caller,
                tenantId,
                subjectId
        );
    }

    private String resolveTenant(
            GrpcCaller caller,
            RequestContext requestContext
    ) {
        String authenticatedTenant =
                normalize(caller.tenantId());

        if (authenticatedTenant == null) {
            throw Status.UNAUTHENTICATED
                    .withDescription(
                            "Authenticated tenant is missing"
                    )
                    .asRuntimeException();
        }

        String requestedTenant =
                normalize(requestContext.getTenantId());

        if (requestedTenant == null) {
            return authenticatedTenant;
        }

        if (requestedTenant.equals(authenticatedTenant)) {
            return authenticatedTenant;
        }

        if (caller.hasAuthorityOrSuperAdmin(CROSS_TENANT)) {
            return requestedTenant;
        }

        throw Status.PERMISSION_DENIED
                .withDescription(
                        "context.tenant_id does not match "
                                + "authenticated tenant"
                )
                .asRuntimeException();
    }

    private String resolveSubject(
            GrpcCaller caller,
            String requestedSubjectId
    ) {
        String authenticatedSubject =
                normalize(caller.subjectId());

        if (authenticatedSubject == null) {
            throw Status.UNAUTHENTICATED
                    .withDescription(
                            "Authenticated subject is missing"
                    )
                    .asRuntimeException();
        }

        String subjectId = normalize(
                requestedSubjectId
        );

        return subjectId == null
                ? authenticatedSubject
                : subjectId;
    }

    private void validateContextActor(
            GrpcCaller caller,
            RequestContext requestContext
    ) {
        String actorId = normalize(
                requestContext.getActorId()
        );

        if (actorId == null) {
            return;
        }

        if (actorId.equals(caller.subjectId())) {
            return;
        }

        /*
         * Cho phép trusted service thực hiện tác vụ thay người dùng
         * nếu có quyền impersonate.
         */
        if (caller.hasAuthorityOrSuperAdmin(
                PRINCIPAL_IMPERSONATE
        )) {
            return;
        }

        throw Status.PERMISSION_DENIED
                .withDescription(
                        "context.actor_id does not match "
                                + "authenticated subject"
                )
                .asRuntimeException();
    }

    private void validateCorrelation(
            GrpcCaller caller,
            RequestContext requestContext
    ) {
        String requestCorrelation = normalize(
                requestContext.getCorrelationId()
        );

        if (requestCorrelation == null) {
            return;
        }

        if (requestCorrelation.length() > 128) {
            throw Status.INVALID_ARGUMENT
                    .withDescription(
                            "context.correlation_id must not exceed "
                                    + "128 characters"
                    )
                    .asRuntimeException();
        }

        String headerCorrelation = normalize(
                caller.correlationId()
        );

        if (headerCorrelation != null
                && !headerCorrelation.equals(requestCorrelation)) {
            throw Status.INVALID_ARGUMENT
                    .withDescription(
                            "context.correlation_id does not match "
                                    + "x-correlation-id"
                    )
                    .asRuntimeException();
        }
    }

    private void requireAuthority(
            GrpcCaller caller,
            String authority
    ) {
        if (!caller.hasAuthorityOrSuperAdmin(authority)) {
            throw Status.PERMISSION_DENIED
                    .withDescription(
                            "Missing required authority: "
                                    + authority
                    )
                    .asRuntimeException();
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    public record SecuredRequest(
            GrpcCaller caller,
            String tenantId,
            String subjectId
    ) {
    }

    public SecuredAdminRequest authorizeTenantAdministration(
            RequestContext requestContext,
            String requiredAuthority
    ) {
        if (requestContext == null) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("request context is required")
                    .asRuntimeException();
        }

        GrpcCaller caller = callerProvider.currentCaller();

        requireAuthority(
                caller,
                requiredAuthority
        );

        String tenantId = resolveTenant(
                caller,
                requestContext
        );

        validateContextActor(
                caller,
                requestContext
        );

        validateCorrelation(
                caller,
                requestContext
        );

        return new SecuredAdminRequest(
                caller,
                tenantId,
                caller.subjectId()
        );
    }

    public SecuredPlatformRequest authorizePlatformAdministration(
            RequestContext requestContext,
            String requiredAuthority
    ) {
        if (requestContext == null) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("request context is required")
                    .asRuntimeException();
        }

        GrpcCaller caller = callerProvider.currentCaller();

        requireAuthority(
                caller,
                requiredAuthority
        );

        validateCorrelation(
                caller,
                requestContext
        );

        return new SecuredPlatformRequest(
                caller,
                caller.subjectId()
        );
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