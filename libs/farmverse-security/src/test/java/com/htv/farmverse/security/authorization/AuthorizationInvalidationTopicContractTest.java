package com.htv.farmverse.security.authorization;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class AuthorizationInvalidationTopicContractTest {
 @Test void defaultMatchesIdentityOutbox() { var p=new AuthorizationInvalidationMqttProperties(true,null,null,null,1,null,null); assertThat(p.topicFilter()).isEqualTo("farmverse/+/_global/domain/authorization-invalidated/v1"); }
 @Test void watermarkIsMonotonic() { var s=new InMemoryAuthorizationVersionWatermarkStore(); s.observeTenant("t",7); s.observeTenant("t",5); s.observeSubject("t","u",8); s.observeSubject("t","u",6); assertThat(s.tenantVersion("t")).isEqualTo(7); assertThat(s.subjectVersion("t","u")).isEqualTo(8); }
}
