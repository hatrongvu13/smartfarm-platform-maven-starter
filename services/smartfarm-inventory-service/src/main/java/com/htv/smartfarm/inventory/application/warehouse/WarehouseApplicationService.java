package com.htv.smartfarm.inventory.application.warehouse;

import java.time.Clock;
import java.util.*;

import com.htv.smartfarm.inventory.application.query.*;
import com.htv.smartfarm.inventory.domain.warehouse.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseApplicationService {
    private final WarehouseJpaRepository warehouses;
    private final WarehouseStockQueryRepository stock;
    private final Clock clock = Clock.systemUTC();

    public WarehouseApplicationService(WarehouseJpaRepository warehouses, WarehouseStockQueryRepository stock) {
        this.warehouses = warehouses;
        this.stock = stock;
    }

    @Transactional
    public WarehouseEntity create(String tenant, String actor, String id, String farm, String code, String name, String description, String address) {
        if (warehouses.existsByTenantIdAndFarmIdAndCodeIgnoreCase(tenant, farm, code))
            throw new IllegalStateException("warehouse code already exists");
        return warehouses.save(new WarehouseEntity(id == null || id.isBlank() ? UUID.randomUUID().toString() : id, tenant, farm, code, name, description, address, actor, clock.millis()));
    }

    @Transactional(readOnly = true)
    public WarehouseEntity get(String tenant, String id) {
        return warehouses.findByTenantIdAndId(tenant, id).orElseThrow(() -> new IllegalArgumentException("warehouse not found"));
    }

    @Transactional(readOnly = true)
    public List<WarehouseEntity> list(String tenant, String farm, WarehouseStatus status, String q, int size) {
        return warehouses.search(tenant, clean(farm), status, clean(q), PageRequest.of(0, limit(size))).getContent();
    }

    @Transactional(readOnly = true)
    public List<WarehouseStockView> items(String tenant, String id, String q, boolean lowOnly, int size) {
        get(tenant, id);
        return stock.list(tenant, id, clean(q), PageRequest.of(0, Math.min(500, limit(size) * 2))).stream().filter(v -> !lowOnly || v.lowStock()).limit(limit(size)).toList();
    }

    private static int limit(int n) {
        return Math.max(1, Math.min(n <= 0 ? 50 : n, 200));
    }

    private static String clean(String v) {
        return v == null ? "" : v.trim();
    }
}
