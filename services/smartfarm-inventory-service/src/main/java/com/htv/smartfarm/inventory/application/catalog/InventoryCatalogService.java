package com.htv.smartfarm.inventory.application.catalog;

import com.htv.smartfarm.inventory.domain.item.*;
import com.htv.smartfarm.inventory.domain.warehouse.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCatalogService {
    private final ItemJpaRepository items;
    private final WarehouseJpaRepository warehouses;
    public InventoryCatalogService(ItemJpaRepository items, WarehouseJpaRepository warehouses) {
        this.items=items; this.warehouses=warehouses;
    }
    @Transactional
    public ItemEntity updateItem(String tenant,String id,String name,String unit,String category,String threshold) {
        ItemEntity value=items.findByTenantIdAndId(tenant,required(id,"itemId"))
                .orElseThrow(()->new IllegalArgumentException("item not found"));
        value.update(name,unit,category,threshold);
        return items.save(value);
    }
    @Transactional
    public ItemEntity setItemActive(String tenant,String id,boolean active) {
        ItemEntity value=items.findByTenantIdAndId(tenant,required(id,"itemId"))
                .orElseThrow(()->new IllegalArgumentException("item not found"));
        value.setActive(active); return items.save(value);
    }
    @Transactional
    public WarehouseEntity updateWarehouse(String tenant,String id,String name,String description,String address) {
        WarehouseEntity value=warehouses.findByTenantIdAndId(tenant,required(id,"warehouseId"))
                .orElseThrow(()->new IllegalArgumentException("warehouse not found"));
        value.update(name,description,address); return warehouses.save(value);
    }
    @Transactional
    public WarehouseEntity setWarehouseActive(String tenant,String id,boolean active) {
        WarehouseEntity value=warehouses.findByTenantIdAndId(tenant,required(id,"warehouseId"))
                .orElseThrow(()->new IllegalArgumentException("warehouse not found"));
        value.setActive(active); return warehouses.save(value);
    }
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
}
