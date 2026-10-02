package com.htv.smartfarm.order.outbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderOutboxProperties.class)
public class OrderOutboxConfiguration {
}
