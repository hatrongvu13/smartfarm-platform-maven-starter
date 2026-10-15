package com.htv.smartfarm.identity.mfa.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Set;

import com.htv.smartfarm.identity.mfa.config.MfaProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

class MfaEncryptionKeyProviderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndReusesOwnerOnlyDevelopmentKey() throws Exception {
        Path keyFile = temporaryDirectory.resolve(".local/mfa.key");
        MfaProperties properties = new MfaProperties();
        properties.setDevelopmentKeyFile(keyFile.toString());
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenAnswer(invocation ->
                ((Profiles) invocation.getArgument(0)).matches("dev"::equals));

        var first = new MfaEncryptionKeyProvider(properties, environment);
        byte[] firstKey = first.key().getEncoded();
        var second = new MfaEncryptionKeyProvider(properties, environment);

        assertThat(Files.exists(keyFile)).isTrue();
        assertThat(Base64.getDecoder().decode(Files.readString(keyFile).trim()))
                .containsExactly(firstKey);
        assertThat(second.key().getEncoded()).containsExactly(firstKey);
        assertThat(first.key().getAlgorithm()).isEqualTo("AES");
        try {
            assertThat(Files.getPosixFilePermissions(keyFile))
                    .isEqualTo(Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE
                    ));
        } catch (UnsupportedOperationException ignored) {
            // The POSIX permission assertion is not available on every filesystem.
        }

        var cipher = new AesGcmSecretCipher(first);
        assertThat(cipher.decrypt(cipher.encrypt("totp-secret")))
                .isEqualTo("totp-secret");
    }

    @Test
    void productionRequiresAnExternallyConfiguredKeyAtStartup() {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        assertThatIllegalStateException()
                .isThrownBy(() -> new MfaEncryptionKeyProvider(
                        new MfaProperties(),
                        environment
                ))
                .withMessageContaining("IDENTITY_MFA_ENCRYPTION_KEY");
    }
}
