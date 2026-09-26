package com.htv.smartfarm.livestock.config;

import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class LivestockGrpcPolicyConfiguration {

    @Bean
    GrpcMethodPolicy livestockGrpcMethodPolicy() {
        return new GrpcMethodPolicy(
                Map.of(
                        "smartfarm.livestock.v1.LivestockTaskService/CreateTask",
                        "SCOPE_tasks:write",

                        "smartfarm.livestock.v1.LivestockTaskService/GetTask",
                        "SCOPE_farm:read"
                ),
                Set.of(
                        "grpc.health.v1.Health/Check",
                        "grpc.health.v1.Health/Watch"
                )
        );
    }

}