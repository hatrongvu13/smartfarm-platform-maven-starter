package com.htv.smartfarm.identity.bootstrap;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityBootstrapLockInitializer {

    @Bean
    SmartInitializingSingleton initializeIdentityBootstrapLock(
            IdentityBootstrapLockRepository lockRepository
    ) {
        return () -> {
            if (!lockRepository.existsById(
                    IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID
            )) {
                lockRepository.save(new IdentityBootstrapLockEntity(
                        IdentityBootstrapLockEntity.SUPERADMIN_LOCK_ID
                ));
            }
        };
    }
}
