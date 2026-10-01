package com.htv.smartfarm.security.autoconfigure;

import com.htv.smartfarm.security.config.SmartFarmSecurityProperties;
import com.htv.smartfarm.security.grpc.autoconfigure.SmartFarmGrpcSecurityAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@AutoConfigureBefore(SmartFarmGrpcSecurityAutoConfiguration.class)
@EnableConfigurationProperties(SmartFarmSecurityProperties.class)
public class SmartFarmSecurityPropertiesAutoConfiguration {
}
