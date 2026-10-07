package com.htv.smartfarm.health.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "smartfarm.health.outbox", name = "enabled", havingValue = "true")
public class HealthOutboxScheduling {}
