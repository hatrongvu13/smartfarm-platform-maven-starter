package com.htv.smartfarm.inventory.config;

import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;

import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InventoryGrpcPolicyConfiguration {
    @Bean
    GrpcMethodPolicy inventoryGrpcMethodPolicy() {
        return new GrpcMethodPolicy(Map.of(
                "smartfarm.inventory.v1.InventoryService/CreateItem", "SCOPE_inventory:write",
                "smartfarm.inventory.v1.InventoryService/ReceiveStock", "SCOPE_inventory:write",
                "smartfarm.inventory.v1.InventoryService/IssueStock", "SCOPE_inventory:write",
                "smartfarm.inventory.v1.InventoryService/GetStockBalance", "SCOPE_inventory:read"),
                Set.of("grpc.health.v1.Health/Check", "grpc.health.v1.Health/Watch"));
    }
}
