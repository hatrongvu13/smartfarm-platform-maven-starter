package com.htv.smartfarm.identity.mfa.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "smartfarm.identity.mfa")
public class MfaProperties {

    private String issuer = "SmartFarm";
    private Duration challengeTtl = Duration.ofMinutes(5);
    private int maximumAttempts = 5;
    private int recoveryCodeCount = 10;
    private String encryptionKey = "";

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public Duration getChallengeTtl() { return challengeTtl; }
    public void setChallengeTtl(Duration challengeTtl) { this.challengeTtl = challengeTtl; }
    public int getMaximumAttempts() { return maximumAttempts; }
    public void setMaximumAttempts(int maximumAttempts) { this.maximumAttempts = maximumAttempts; }
    public int getRecoveryCodeCount() { return recoveryCodeCount; }
    public void setRecoveryCodeCount(int recoveryCodeCount) { this.recoveryCodeCount = recoveryCodeCount; }
    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }

    public void validateRuntime() {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("smartfarm.identity.mfa.issuer must not be blank");
        }
        if (challengeTtl == null || challengeTtl.isZero() || challengeTtl.isNegative()) {
            throw new IllegalStateException("smartfarm.identity.mfa.challenge-ttl is invalid");
        }
        if (maximumAttempts < 1 || maximumAttempts > 20) {
            throw new IllegalStateException("smartfarm.identity.mfa.maximum-attempts is invalid");
        }
        if (recoveryCodeCount < 1 || recoveryCodeCount > 20) {
            throw new IllegalStateException("smartfarm.identity.mfa.recovery-code-count is invalid");
        }
    }
}
