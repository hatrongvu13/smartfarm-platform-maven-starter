package com.htv.farmverse.identity.mfa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, String> {

    List<RecoveryCode> findByUserIdAndUsedAtIsNull(String userId);

    long countByUserIdAndUsedAtIsNull(String userId);

    void deleteByUserId(String userId);
}
