package com.htv.farmverse.gateway.graphql;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class IdentityGraphQlAuthorizationContractTest {
 @Test void exactScopeOrWildcardAndVersionAwareDelegation() throws Exception {
  String s=Files.readString(Path.of("src/main/java/com/htv/farmverse/gateway/graphql/IdentityGraphQlController.java"));
  assertThat(s).contains("SCOPE_identity:role:read') or hasAuthority('SCOPE_*");
  assertThat(s).contains("SCOPE_identity:permission:read') or hasAuthority('SCOPE_*");
  assertThat(s).contains("tokens.tokenFor(audience, jwt)");
 }
}
