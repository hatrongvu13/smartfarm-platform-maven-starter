package com.htv.farmverse.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class InMemoryAuthorizationVersionWatermarkStoreTest {
    @Test void monotonicAndStale() {
        var s=new InMemoryAuthorizationVersionWatermarkStore();
        assertThat(s.observeTenant("t",7)).isEqualTo(7);
        assertThat(s.observeTenant("t",3)).isEqualTo(7);
        assertThat(s.observeSubject("t","u",5)).isEqualTo(5);
        assertThat(s.observeSubject("t","u",2)).isEqualTo(5);
        assertThat(s.isStale("t","u",6,5)).isTrue();
        assertThat(s.isStale("t","u",7,5)).isFalse();
    }
}
