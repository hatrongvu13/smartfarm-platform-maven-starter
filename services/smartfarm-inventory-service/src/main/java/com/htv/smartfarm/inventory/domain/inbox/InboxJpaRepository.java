package com.htv.smartfarm.inventory.domain.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA access to {@link InboxEntity}.
 */
public interface InboxJpaRepository extends JpaRepository<InboxEntity, String> {
}
