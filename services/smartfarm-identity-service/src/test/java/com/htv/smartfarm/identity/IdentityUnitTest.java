package com.htv.smartfarm.identity;
import com.htv.smartfarm.identity.config.IdentitySettings;
import com.htv.smartfarm.identity.token.RefreshRepository;
import com.htv.smartfarm.identity.auth.AuthService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class IdentityUnitTest {
 @Test void refreshTokenIsStoredAsDeterministicDigest() {
  String hash=RefreshRepository.digest("test-secret");
  assertEquals(64,hash.length());assertEquals(hash,RefreshRepository.digest("test-secret"));
  assertNotEquals("test-secret",hash);
 }
 @Test void passwordPolicyRejectsShortValues() {
  assertThrows(ResponseStatusException.class,()->AuthService.password("short"));
  assertDoesNotThrow(()->AuthService.password("long-enough-password"));
 }
 @Test void tokenLifetimeMustBeShort() {
  assertThrows(IllegalArgumentException.class,()->new IdentitySettings("https://id.example","smartfarm-gateway","kid","private.pem","public.pem",Duration.ofHours(1),Duration.ofDays(7),5,Duration.ofMinutes(15),false,"","","",""));
 }
}
