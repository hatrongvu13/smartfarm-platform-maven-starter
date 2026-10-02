package com.htv.smartfarm.order.saga.persistence;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderSagaProperties.class)
public class OrderSagaPersistenceConfiguration {
}
