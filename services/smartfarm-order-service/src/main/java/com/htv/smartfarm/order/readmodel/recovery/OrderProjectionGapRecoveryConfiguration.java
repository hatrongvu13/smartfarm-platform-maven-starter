package com.htv.smartfarm.order.readmodel.recovery;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderProjectionGapRecoveryProperties.class)
public class OrderProjectionGapRecoveryConfiguration {
}
