package com.htv.smartfarm.identity.mfa.crypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

import javax.crypto.spec.SecretKeySpec;

import com.htv.smartfarm.identity.mfa.config.MfaProperties;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MfaEncryptionKeyProvider {

    private static final int KEY_LENGTH_BYTES = 32;
    private static final Set<PosixFilePermission> OWNER_ONLY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    private final SecretKeySpec key;

    public MfaEncryptionKeyProvider(
            MfaProperties properties,
            Environment environment
    ) {
        String configured = properties.getEncryptionKey();
        if (configured == null || configured.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("dev"))) {
                configured = loadOrCreateDevelopmentKey(
                        Path.of(properties.getDevelopmentKeyFile())
                );
            } else if (!environment.acceptsProfiles(Profiles.of("test"))) {
                throw new IllegalStateException(
                        "IDENTITY_MFA_ENCRYPTION_KEY is required outside the dev/test profiles"
                );
            }
        }
        this.key = configured == null || configured.isBlank()
                ? null
                : decodeKey(configured);
    }

    public SecretKeySpec key() {
        if (key == null) {
            throw new IllegalStateException(
                    "MFA encryption key is not configured for this profile"
            );
        }
        return key;
    }

    private static synchronized String loadOrCreateDevelopmentKey(Path configuredPath) {
        Path path = configuredPath.toAbsolutePath().normalize();
        try {
            Path parent = path.getParent();
            if (parent == null) {
                throw new IllegalStateException("Development MFA key path must have a parent directory");
            }
            Files.createDirectories(parent);
            if (Files.isSymbolicLink(path)) {
                throw new IllegalStateException("Development MFA key path must not be a symbolic link");
            }
            try (FileChannel channel = openKeyFile(path)) {
                restrictPermissions(path);
                try (FileLock ignored = channel.lock()) {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IllegalStateException("Development MFA key path must be a regular file");
                    }
                    if (channel.size() == 0) {
                        byte[] generated = new byte[KEY_LENGTH_BYTES];
                        new SecureRandom().nextBytes(generated);
                        ByteBuffer contents = StandardCharsets.US_ASCII.encode(
                                Base64.getEncoder().encodeToString(generated)
                        );
                        channel.position(0);
                        while (contents.hasRemaining()) {
                            channel.write(contents);
                        }
                        channel.force(true);
                        log.info("Generated local development MFA encryption key at {}", path);
                    }
                    if (channel.size() > 128) {
                        throw new IllegalStateException(
                                "Development MFA key file has an invalid length: " + path
                        );
                    }
                    ByteBuffer contents = ByteBuffer.allocate((int) channel.size());
                    channel.position(0);
                    while (contents.hasRemaining() && channel.read(contents) >= 0) {
                        // Read until the complete file is loaded.
                    }
                    return StandardCharsets.US_ASCII.decode(contents.flip()).toString().trim();
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not load or create the local development MFA key at " + path,
                    exception
            );
        }
    }

    private static FileChannel openKeyFile(Path path) throws IOException {
        Set<OpenOption> options = Set.of(
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
        );
        try {
            return FileChannel.open(
                    path,
                    options,
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_PERMISSIONS)
            );
        } catch (UnsupportedOperationException exception) {
            return FileChannel.open(
                    path,
                    Set.of(
                            StandardOpenOption.CREATE,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS
                    )
            );
        }
    }

    private static void restrictPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, OWNER_ONLY_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            if (!Files.isReadable(path) || !Files.isWritable(path)) {
                throw new IOException("Development MFA key file must be readable and writable");
            }
        }
    }

    private static SecretKeySpec decodeKey(String configured) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MFA encryption key must be Base64", exception);
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("MFA encryption key must decode to 32 bytes");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
