package com.htv.smartfarm.identity.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.smartfarm.common.contract.PermissionCodeContract;
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
}
