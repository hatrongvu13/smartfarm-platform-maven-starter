package com.htv.smartfarm.health.outbox;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA access to {@link HealthOutboxEntity}. */
public interface HealthOutboxJpaRepository extends JpaRepository<HealthOutboxEntity, String> {

    List<HealthOutboxEntity> findByStatusOrderByEventId(String status, Pageable pageable);
}
