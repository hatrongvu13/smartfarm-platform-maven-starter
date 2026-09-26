package com.htv.smartfarm.security.jwt;
import com.htv.smartfarm.security.config.SecurityProperties;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
/** In addition to signature verification, require issuer, time, audience, subject and tenant. */
public final class SmartFarmJwtValidator implements OAuth2TokenValidator<Jwt> {
 private final SecurityProperties properties;
 private final OAuth2TokenValidator<Jwt> defaults;
 public SmartFarmJwtValidator(SecurityProperties properties) {
  this.properties = properties;
  this.defaults = JwtValidators.createDefaultWithIssuer(properties.issuer());
 }
 @Override public OAuth2TokenValidatorResult validate(Jwt jwt) {
  OAuth2TokenValidatorResult standard = defaults.validate(jwt);
  if (standard.hasErrors()) return standard;
  if (!jwt.getAudience().contains(properties.audience())) return fail("audience_mismatch");
  if (jwt.getSubject() == null || jwt.getSubject().isBlank()) return fail("missing_subject");
  if (jwt.getExpiresAt() == null) return fail("missing_expiry");
  String tenant = jwt.getClaimAsString("tenant_id");
  if (tenant == null || tenant.isBlank()) return fail("missing_tenant");
  return OAuth2TokenValidatorResult.success();
 }
 private static OAuth2TokenValidatorResult fail(String code) {
  return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", code, null));
 }
}
