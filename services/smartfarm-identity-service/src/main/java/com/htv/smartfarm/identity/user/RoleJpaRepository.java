package com.htv.smartfarm.identity.user;

import org.springframework.data.jpa.repository.JpaRepository;


/** Spring Data JPA access to {@code sf_role}. */
public interface RoleJpaRepository extends JpaRepository<RoleEntity, String> {
}
