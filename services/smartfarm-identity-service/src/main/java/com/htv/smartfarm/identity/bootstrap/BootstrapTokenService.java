package com.htv.smartfarm.identity.bootstrap;

import com.htv.smartfarm.identity.config.SuperAdminBootstrapProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BootstrapTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdentityBootstrapLockRepository lockRepository;
    private final SuperAdminBootstrapProperties properties;

    @Transactional
    public void initializeIfRequired(boolean superAdminExists) {
        IdentityBootstrapLockEntity lock = lockRepository
                .findByIdForUpdate(IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID)
                .orElseGet(() -> lockRepository.saveAndFlush(
                        new IdentityBootstrapLockEntity(
                                IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID
                        )
                ));

        if (superAdminExists || configuredTokenPresent()
                || lock.getBootstrapTokenHash() != null) {
            return;
        }

        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        lock.setBootstrapTokenHash(hash(token));
        lockRepository.save(lock);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.warn("""
                        Initial Identity setup detected. Use this one-time bootstrap key \
                        in the SmartFarm welcome screen to create the first administrator: {}
                        """, token);
            }
        });
    }

    public boolean matches(String providedToken) {
        if (providedToken == null || providedToken.isBlank()) {
            return false;
        }

        String configuredToken = properties.token();
        if (configuredToken != null && !configuredToken.isBlank()) {
            return MessageDigest.isEqual(
                    configuredToken.getBytes(StandardCharsets.UTF_8),
                    providedToken.getBytes(StandardCharsets.UTF_8)
            );
        }

        String storedHash = lockRepository
                .findById(IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID)
                .map(IdentityBootstrapLockEntity::getBootstrapTokenHash)
                .orElse(null);
        return storedHash != null && MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.US_ASCII),
                hash(providedToken).getBytes(StandardCharsets.US_ASCII)
        );
    }

    @Transactional
    public void consume() {
        lockRepository.findByIdForUpdate(IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID)
                .ifPresent(lock -> {
                    lock.setBootstrapTokenHash(null);
                    lockRepository.save(lock);
                });
    }

    private boolean configuredTokenPresent() {
        return properties.token() != null && !properties.token().isBlank();
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
