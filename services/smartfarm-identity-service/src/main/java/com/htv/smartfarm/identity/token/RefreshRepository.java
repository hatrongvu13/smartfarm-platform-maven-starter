package com.htv.smartfarm.identity.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RefreshRepository {

    public record Session(
            String id,
            String userId,
            String tenantId,
            String audience,
            String familyId,
            long expiresAt,
            boolean revoked
    ) {
    }

    private final RefreshJpaRepository refresh;

    public RefreshRepository(RefreshJpaRepository refresh) {
        this.refresh = refresh;
    }

    public static String digest(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("refresh token must not be blank");
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public void insert(
            String id,
            String userId,
            String tenantId,
            String audience,
            String familyId,
            String raw,
            long expiry
    ) {
        refresh.save(new RefreshEntity(
                id,
                userId,
                tenantId,
                audience,
                familyId,
                digest(raw),
                expiry,
                false
        ));
    }

    public Optional<Session> find(String raw) {
        return refresh.findByTokenHash(digest(raw)).map(entity ->
                new Session(
                        entity.getId(),
                        entity.getUserId(),
                        entity.getTenantId(),
                        entity.getAudience(),
                        entity.getFamilyId(),
                        entity.getExpiresAt(),
                        entity.isRevoked()
                )
        );
    }

    @Transactional
    public boolean consume(String id) {
        return refresh.consumeIfActive(id) == 1;
    }

    @Transactional
    public void revokeFamily(String familyId) {
        refresh.revokeFamily(familyId);
    }

    @Transactional
    public void revokeUser(String userId) {
        refresh.revokeUser(userId);
    }

    @Transactional
    public void revokeUserInTenant(String userId, String tenantId) {
        refresh.revokeUserInTenant(userId, tenantId);
    }
}
