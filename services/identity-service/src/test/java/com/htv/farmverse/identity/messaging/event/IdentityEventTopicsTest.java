package com.htv.farmverse.identity.messaging.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.farmverse.common.contract.IdentityEventContract;
import org.junit.jupiter.api.Test;

class IdentityEventTopicsTest {

    @Test
    void buildsAuthorizationInvalidationTopicFromSharedContract() {
        assertThat(IdentityEventTopics.domain(
                "tenant-1",
                IdentityEventContract.AUTHORIZATION_INVALIDATED_EVENT_NAME,
                1
        )).isEqualTo("farmverse/tenant-1/_global/domain/authorization-invalidated/v1");
    }
}
