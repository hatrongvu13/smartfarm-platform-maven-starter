package com.htv.smartfarm.security.jwt;
import com.htv.smartfarm.security.config.SecurityProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
/** RS256 allowlist; public JWKS only. Does not fetch arbitrary URLs from token claims. */
public final class JwtSecurityFactory {
 private JwtSecurityFactory() {}
 public static JwtDecoder servletDecoder(SecurityProperties p) {
  NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(p.jwkSetUri()).jwsAlgorithm(SignatureAlgorithm.RS256).build();
  decoder.setJwtValidator(new SmartFarmJwtValidator(p));
  return decoder;
 }
 public static ReactiveJwtDecoder reactiveDecoder(SecurityProperties p) {
  NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(p.jwkSetUri()).jwsAlgorithm(SignatureAlgorithm.RS256).build();
  decoder.setJwtValidator(new SmartFarmJwtValidator(p));
  return decoder;
 }
}
