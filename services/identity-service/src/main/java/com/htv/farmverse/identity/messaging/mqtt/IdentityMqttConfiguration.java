package com.htv.farmverse.identity.messaging.mqtt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityMqttProperties.class)
public class IdentityMqttConfiguration {
}
