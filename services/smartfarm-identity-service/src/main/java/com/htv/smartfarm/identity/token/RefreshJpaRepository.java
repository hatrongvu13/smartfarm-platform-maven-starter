package com.htv.smartfarm.identity.token;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA access to {@link RefreshEntity}. */
public interface RefreshJpaRepository extends JpaRepository<RefreshEntity, String> {

    Optional<RefreshEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshEntity r set r.revoked = true where r.familyId = :family")
    void revokeFamily(@Param("family") String family);

    @Modifying
    @Query("update RefreshEntity r set r.revoked = true where r.userId = :user")
    void revokeUser(@Param("user") String user);
}
