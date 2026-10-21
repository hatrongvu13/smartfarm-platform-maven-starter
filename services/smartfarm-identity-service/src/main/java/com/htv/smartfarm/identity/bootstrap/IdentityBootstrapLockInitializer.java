package com.htv.smartfarm.identity.bootstrap;

import com.htv.smartfarm.identity.authorization.repository.MembershipRoleRepository;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityBootstrapLockInitializer {

    @Bean
    SmartInitializingSingleton initializeIdentityBootstrapLock(
            BootstrapTokenService bootstrapTokens,
            MembershipRoleRepository membershipRoles
    ) {
        return () -> bootstrapTokens.initializeIfRequired(
                membershipRoles.countByRoleCode("SUPERADMIN") > 0
        );
    }
}
