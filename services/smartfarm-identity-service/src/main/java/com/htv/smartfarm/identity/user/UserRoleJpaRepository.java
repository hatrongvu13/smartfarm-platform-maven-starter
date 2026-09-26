package com.htv.smartfarm.identity.user;

import org.springframework.data.jpa.repository.JpaRepository;


/** Spring Data JPA access to {@code sf_user_role}. */
public interface UserRoleJpaRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.Key> {
}
