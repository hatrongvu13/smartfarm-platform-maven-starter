package com.htv.farmverse.security.autoconfigure;

import com.htv.farmverse.security.config.FarmVerseSecurityProperties;
import com.htv.farmverse.security.grpc.autoconfigure.FarmVerseGrpcSecurityAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@AutoConfigureBefore(FarmVerseGrpcSecurityAutoConfiguration.class)
@EnableConfigurationProperties(FarmVerseSecurityProperties.class)
public class FarmVerseSecurityPropertiesAutoConfiguration {
}
