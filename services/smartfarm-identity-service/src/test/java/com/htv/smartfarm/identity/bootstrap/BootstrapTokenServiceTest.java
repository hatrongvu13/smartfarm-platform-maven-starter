package com.htv.smartfarm.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.htv.smartfarm.identity.config.SuperAdminBootstrapProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BootstrapTokenServiceTest {

    private final IdentityBootstrapLockRepository locks =
            mock(IdentityBootstrapLockRepository.class);

    @Test
    void matchesGeneratedTokenUsingStoredHash() throws Exception {
        String token = "generated-one-time-key";
        IdentityBootstrapLockEntity lock =
                new IdentityBootstrapLockEntity(IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID);
        lock.setBootstrapTokenHash(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(token.getBytes(StandardCharsets.UTF_8))
        ));
        when(locks.findById(IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID))
                .thenReturn(Optional.of(lock));

        BootstrapTokenService service = new BootstrapTokenService(
                locks,
                new SuperAdminBootstrapProperties("")
        );

        assertThat(service.matches(token)).isTrue();
        assertThat(service.matches("incorrect-key")).isFalse();
    }

    @Test
    void configuredTokenOverridesGeneratedTokenHash() {
        IdentityBootstrapLockEntity lock =
                new IdentityBootstrapLockEntity(IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID);
        lock.setBootstrapTokenHash("a".repeat(64));
        when(locks.findById(IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID))
                .thenReturn(Optional.of(lock));

        BootstrapTokenService service = new BootstrapTokenService(
                locks,
                new SuperAdminBootstrapProperties("configured-bootstrap-key-long-enough")
        );

        assertThat(service.matches("configured-bootstrap-key-long-enough")).isTrue();
        assertThat(service.matches("generated-key")).isFalse();
    }

    @Test
    void consumeRemovesGeneratedCredential() {
        IdentityBootstrapLockEntity lock =
                new IdentityBootstrapLockEntity(IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID);
        lock.setBootstrapTokenHash("a".repeat(64));
        when(locks.findByIdForUpdate(IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID))
                .thenReturn(Optional.of(lock));

        new BootstrapTokenService(locks, new SuperAdminBootstrapProperties(""))
                .consume();

        assertThat(lock.getBootstrapTokenHash()).isNull();
        verify(locks).save(lock);
    }
}
