package com.htv.farmverse.identity.mfa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTotpRepository extends JpaRepository<UserTotp, String> {
}
