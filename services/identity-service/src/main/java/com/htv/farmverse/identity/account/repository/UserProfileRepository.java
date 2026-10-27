package com.htv.farmverse.identity.account.repository;

import java.util.Optional;

import com.htv.farmverse.identity.account.domain.UserProfileEntity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository
        extends JpaRepository<UserProfileEntity, String> {

    @EntityGraph(attributePaths = "account")
    Optional<UserProfileEntity> findWithAccountByUserId(String userId);
}