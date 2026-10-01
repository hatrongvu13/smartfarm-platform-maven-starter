package com.htv.smartfarm.identity.messaging.outbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(IdentityOutboxProperties.class)
public class IdentityOutboxConfiguration {
}
