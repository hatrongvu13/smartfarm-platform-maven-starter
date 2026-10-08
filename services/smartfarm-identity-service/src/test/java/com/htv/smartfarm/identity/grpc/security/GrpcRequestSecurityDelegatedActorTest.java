package com.htv.smartfarm.identity.grpc.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Set;

import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.security.core.TokenType;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcRequestSecurityDelegatedActorTest {

    @Mock private GrpcCallerProvider callers;
    private GrpcRequestSecurity security;

    @BeforeEach
    void setUp() {
        security = new GrpcRequestSecurity(callers);
    }

    @Test
    void trustedGatewayMayPropagateHumanActorForMe() {
        when(callers.currentCaller()).thenReturn(serviceCaller(
                Set.of(IdentityGrpcAuthorities.PRINCIPAL_READ,
                        IdentityGrpcAuthorities.PRINCIPAL_IMPERSONATE)));

        var secured = security.authorizePrincipalRead(context("user-001"), "");

        assertThat(secured.subjectId()).isEqualTo("user-001");
        assertThat(secured.actorId()).isEqualTo("user-001");
        assertThat(secured.caller().subjectId()).isEqualTo("svc:gateway");
    }

    @Test
    void trustedGatewayCredentialSelfUsesVerifiedHumanActor() {
        when(callers.currentCaller()).thenReturn(serviceCaller(
                Set.of(IdentityGrpcAuthorities.SECURITY_READ,
                        IdentityGrpcAuthorities.PRINCIPAL_IMPERSONATE)));

        var secured = security.authorizeCredentialSelf(
                context("user-001"), "user-001", IdentityGrpcAuthorities.SECURITY_READ);

        assertThat(secured.subjectId()).isEqualTo("user-001");
        assertThat(secured.actorId()).isEqualTo("user-001");
    }

    @Test
    void serviceWithoutDelegationAuthorityCannotInjectActor() {
        when(callers.currentCaller()).thenReturn(serviceCaller(
                Set.of(IdentityGrpcAuthorities.PRINCIPAL_READ)));

        assertDenied(() -> security.authorizePrincipalRead(context("user-001"), ""));
    }

    @Test
    void userTokenCannotInjectAnotherActorEvenWithOrdinaryReadScope() {
        when(callers.currentCaller()).thenReturn(new GrpcCaller(
                "user-001", null, "tenant-001",
                Set.of(IdentityGrpcAuthorities.PRINCIPAL_READ),
                Set.of("smartfarm-identity"), TokenType.USER, "corr-001"));

        assertDenied(() -> security.authorizePrincipalRead(context("user-002"), ""));
    }

    @Test
    void delegatedActorIsReturnedForAdministrationAudit() {
        when(callers.currentCaller()).thenReturn(serviceCaller(
                Set.of(IdentityGrpcAuthorities.USER_READ,
                        IdentityGrpcAuthorities.PRINCIPAL_IMPERSONATE)));

        var secured = security.authorizeTenantAdministration(
                context("admin-001"), IdentityGrpcAuthorities.USER_READ);

        assertThat(secured.actorId()).isEqualTo("admin-001");
        assertThat(secured.tenantId()).isEqualTo("tenant-001");
    }

    @Test
    void mismatchedCorrelationRemainsRejected() {
        when(callers.currentCaller()).thenReturn(serviceCaller(
                Set.of(IdentityGrpcAuthorities.PRINCIPAL_READ,
                        IdentityGrpcAuthorities.PRINCIPAL_IMPERSONATE)));
        RequestContext context = context("user-001").toBuilder()
                .setCorrelationId("corr-other").build();

        assertThatThrownBy(() -> security.authorizePrincipalRead(context, ""))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> assertThat(Status.fromThrowable(error).getCode())
                        .isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    private GrpcCaller serviceCaller(Set<String> authorities) {
        return new GrpcCaller(
                "svc:gateway", "gateway", "tenant-001", authorities,
                Set.of("smartfarm-identity"), TokenType.SERVICE, "corr-001");
    }

    private RequestContext context(String actor) {
        return RequestContext.newBuilder()
                .setTenantId("tenant-001")
                .setActorId(actor)
                .setCorrelationId("corr-001")
                .build();
    }

    private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(error -> assertThat(Status.fromThrowable(error).getCode())
                        .isEqualTo(Status.Code.PERMISSION_DENIED));
    }
}
