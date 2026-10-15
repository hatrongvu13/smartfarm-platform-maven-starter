package com.htv.smartfarm.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.smartfarm.identity.grpc.security.IdentityGrpcAuthorities;
import com.htv.smartfarm.proto.identity.v1.IdentityTokenServiceGrpc;
import org.junit.jupiter.api.Test;

class IdentityTokenGrpcPolicyTest {
    @Test
    void tokenIssuanceRequiresAuthenticationAndSessionsKeepAuthorities() {
        var policy = new IdentityGrpcSecurityConfiguration()
                .identityGrpcMethodPolicy();

        assertThat(policy.isPublic(
                IdentityTokenServiceGrpc.getIssueServiceTokenMethod()
                        .getFullMethodName()
        )).isFalse();
        assertThat(policy.isPublic(
                IdentityTokenServiceGrpc.getExchangeDelegatedTokenMethod()
                        .getFullMethodName()
        )).isFalse();
        assertThat(policy.requiredAuthority(
                IdentityTokenServiceGrpc.getListSessionsMethod()
                        .getFullMethodName()
        )).isEqualTo(IdentityGrpcAuthorities.SESSION_READ);
        assertThat(policy.requiredAuthority(
                IdentityTokenServiceGrpc.getRevokeSessionMethod()
                        .getFullMethodName()
        )).isEqualTo(IdentityGrpcAuthorities.SESSION_REVOKE);
        assertThat(policy.requiredAuthority(
                IdentityTokenServiceGrpc.getRevokeAllSessionsMethod()
                        .getFullMethodName()
        )).isEqualTo(IdentityGrpcAuthorities.SESSION_REVOKE);
    }
}
