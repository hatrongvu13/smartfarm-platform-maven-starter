package com.htv.smartfarm.livestock.config;

import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

@Configuration(proxyBeanMethods = false)
public class LivestockGrpcPolicyConfiguration {

    @Bean
    GrpcMethodPolicy livestockGrpcMethodPolicy() {
        return new GrpcMethodPolicy(
                Map.ofEntries(
                        entry("smartfarm.livestock.v1.LivestockTaskService/CreateTask", "SCOPE_tasks:write"),
                        entry("smartfarm.livestock.v1.LivestockTaskService/AssignTask", "SCOPE_tasks:write"),
                        entry("smartfarm.livestock.v1.LivestockTaskService/AcceptTask", "SCOPE_tasks:write"),
                        entry("smartfarm.livestock.v1.LivestockTaskService/CompleteTask", "SCOPE_tasks:write"),
                        entry("smartfarm.livestock.v1.LivestockTaskService/CancelTask", "SCOPE_tasks:write"),
                        entry("smartfarm.livestock.v1.LivestockTaskService/ListTasks", "SCOPE_farm:read"),
                        entry("smartfarm.livestock.v1.LivestockTaskService/RegisterAnimal", "SCOPE_tasks:write"),
                        entry("smartfarm.livestock.v1.LivestockTaskService/GetAnimal", "SCOPE_farm:read"),
                        entry("smartfarm.livestock.v1.LivestockTaskService/ListAnimals", "SCOPE_farm:read"),
                        entry("smartfarm.livestock.v1.LivestockTaskService/CreateSchedule", "SCOPE_tasks:write"),
                        entry("smartfarm.livestock.v1.LivestockTaskService/ListSchedules", "SCOPE_farm:read"),
                        entry("smartfarm.livestock.v1.LivestockTaskService/GetTask", "SCOPE_farm:read")
                ),
                Set.of(
                        "grpc.health.v1.Health/Check",
                        "grpc.health.v1.Health/Watch"
                )
        );
    }

}