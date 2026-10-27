package com.htv.farmverse.identity.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.htv.farmverse.common.contract.PermissionCodeContract;
import org.junit.jupiter.api.Test;

class PermissionCodeTest {

    @Test
    void usesSharedWildcardForNormalizationAndMapping() {
        assertThat(PermissionCode.normalize(PermissionCodeContract.WILDCARD))
                .isEqualTo(PermissionCodeContract.WILDCARD);
        assertThat(PermissionCode.resourceType(PermissionCodeContract.WILDCARD))
                .isEqualTo(PermissionCodeContract.WILDCARD);
        assertThat(PermissionCode.action(PermissionCodeContract.WILDCARD))
                .isEqualTo(PermissionCodeContract.WILDCARD);
    }

    @Test
    void normalizesCanonicalCodesAndRejectsSyntaxNotAcceptedByRuntimeAuthorities() {
        assertThat(PermissionCode.normalize(" Identity:User:Read "))
                .isEqualTo("identity:user:read");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PermissionCode.normalize("identity.user:read"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PermissionCode.normalize("identity_user:read"));
    }
}
