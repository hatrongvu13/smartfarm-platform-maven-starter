package com.htv.farmverse.health.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "farmverse.health.outbox", name = "enabled", havingValue = "true")
public class HealthOutboxScheduling {}
