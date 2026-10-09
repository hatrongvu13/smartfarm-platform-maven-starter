package com.htv.smartfarm.inventory.config;

import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;

import java.util.*;

import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class InventoryGrpcPolicyConfiguration {
    @Bean
    GrpcMethodPolicy inventoryGrpcMethodPolicy() {
        String p = "smartfarm.inventory.v1.InventoryService/";
        return new GrpcMethodPolicy(Map.ofEntries(Map.entry(p + "CreateItem", "SCOPE_inventory:write"), Map.entry(p + "CreateWarehouse", "SCOPE_inventory:write"), Map.entry(p + "GetWarehouse", "SCOPE_inventory:read"), Map.entry(p + "ListWarehouses", "SCOPE_inventory:read"), Map.entry(p + "ListWarehouseItems", "SCOPE_inventory:read"), Map.entry(p + "ReceiveStock", "SCOPE_inventory:write"), Map.entry(p + "IssueStock", "SCOPE_inventory:write"), Map.entry(p + "AdjustStock", "SCOPE_inventory:write"), Map.entry(p + "ReserveStock", "SCOPE_inventory:write"), Map.entry(p + "ReleaseReservation", "SCOPE_inventory:write"), Map.entry(p + "CommitReservation", "SCOPE_inventory:write"), Map.entry(p + "GetStockBalance", "SCOPE_inventory:read"), Map.entry(p + "ListLowStock", "SCOPE_inventory:read"), Map.entry(p + "TraceLot", "SCOPE_inventory:read")), Set.of("grpc.health.v1.Health/Check", "grpc.health.v1.Health/Watch"));
    }
}
