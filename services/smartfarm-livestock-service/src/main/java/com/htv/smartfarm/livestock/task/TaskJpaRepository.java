package com.htv.smartfarm.livestock.task;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA access to {@link TaskEntity}. */
public interface TaskJpaRepository extends JpaRepository<TaskEntity, String> {

    Optional<TaskEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<TaskEntity> findByTenantIdAndId(String tenantId, String id);
}
