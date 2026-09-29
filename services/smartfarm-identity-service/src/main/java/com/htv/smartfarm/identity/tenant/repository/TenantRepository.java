package com.htv.smartfarm.identity.tenant.repository;

import java.util.Optional;

import com.htv.smartfarm.identity.tenant.domain.TenantEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository
        extends JpaRepository<TenantEntity, String> {

    Optional<TenantEntity> findByCode(String code);

    Optional<TenantEntity> findByCodeAndEnabledTrue(String code);

    boolean existsByCode(String code);
}