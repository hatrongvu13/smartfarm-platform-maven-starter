package com.htv.smartfarm.health.config;

import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;

import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class HealthGrpcPolicy {
    @Bean
    GrpcMethodPolicy healthGrpcMethodPolicy() {
        return new GrpcMethodPolicy(Map.of(
                "smartfarm.health.v1.AnimalHealthService/RecordObservation", "SCOPE_health:write",
                "smartfarm.health.v1.AnimalHealthService/ListObservations", "SCOPE_health:read",
                "smartfarm.health.v1.AnimalHealthService/ScheduleExamination", "SCOPE_health:write",
                "smartfarm.health.v1.AnimalHealthService/CompleteExamination", "SCOPE_health:write",
                "smartfarm.health.v1.AnimalHealthService/RecordVaccination", "SCOPE_health:write",
                "smartfarm.health.v1.AnimalHealthService/ListVaccinations", "SCOPE_health:read",
                "smartfarm.health.v1.AnimalHealthService/RecordTreatment", "SCOPE_health:write",
                "smartfarm.health.v1.AnimalHealthService/ListTreatments", "SCOPE_health:read",
                "smartfarm.health.v1.AnimalHealthService/ListAlerts", "SCOPE_health:read",
                "smartfarm.health.v1.AnimalHealthService/AcknowledgeAlert", "SCOPE_health:write"),
                Set.of("grpc.health.v1.Health/Check", "grpc.health.v1.Health/Watch"));
    }
}
