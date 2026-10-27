package com.htv.farmverse.identity.mfa.config;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "farmverse.identity.mfa")
public class MfaProperties {

    private String issuer = "FarmVerse";
    private Duration challengeTtl = Duration.ofMinutes(5);
    private int maximumAttempts = 5;
    private int recoveryCodeCount = 10;
    private String encryptionKey = "";
    private String developmentKeyFile = ".local/identity-mfa-encryption-key";

    public void validateRuntime() {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("farmverse.identity.mfa.issuer must not be blank");
        }
        if (challengeTtl == null || challengeTtl.isZero() || challengeTtl.isNegative()) {
            throw new IllegalStateException("farmverse.identity.mfa.challenge-ttl is invalid");
        }
        if (maximumAttempts < 1 || maximumAttempts > 20) {
            throw new IllegalStateException("farmverse.identity.mfa.maximum-attempts is invalid");
        }
        if (recoveryCodeCount < 1 || recoveryCodeCount > 20) {
            throw new IllegalStateException("farmverse.identity.mfa.recovery-code-count is invalid");
        }
    }
}
