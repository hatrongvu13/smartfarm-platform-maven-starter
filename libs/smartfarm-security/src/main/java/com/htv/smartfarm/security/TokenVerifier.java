package com.htv.smartfarm.security;
import java.util.Set;
/** Kept for compatibility with the previous smartfarm-security starter. */
public interface TokenVerifier {
 VerifiedToken verify(String bearerToken);
 record VerifiedToken(String subject, Set<String> roles, String tenantId) {}
}
