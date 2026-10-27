package com.htv.farmverse.order.observability;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderObservabilityProperties.class)
public class OrderObservabilityConfiguration { }
