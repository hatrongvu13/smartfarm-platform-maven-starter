package com.htv.farmverse.identity.token.session.repository;

import java.time.Instant;
import java.util.Optional;
import com.htv.farmverse.identity.token.session.entity.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenEntity t where t.tokenHash=:hash")
    Optional<RefreshTokenEntity> lockByTokenHash(@Param("hash") String hash);
    @Modifying
    @Query("update RefreshTokenEntity t set t.revokedAt=:now where t.sessionId=:sessionId and t.revokedAt is null")
    int revokeSessionTokens(@Param("sessionId") String sessionId,@Param("now") Instant now);
    @Modifying
    @Query("delete from RefreshTokenEntity t where t.expiresAt<:before")
    int deleteExpired(@Param("before") Instant before);
}
