package com.htv.smartfarm.inventory.application.query;

import com.htv.smartfarm.inventory.domain.item.ItemCategoryValue;
import com.htv.smartfarm.inventory.domain.item.ItemEntity;
import com.htv.smartfarm.inventory.domain.item.ItemJpaRepository;
import com.htv.smartfarm.inventory.domain.lot.LotEntity;
import com.htv.smartfarm.inventory.domain.lot.LotJpaRepository;
import com.htv.smartfarm.inventory.domain.movement.MovementEntity;
import com.htv.smartfarm.inventory.domain.movement.MovementJpaRepository;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryQueryService {

    private final ItemJpaRepository items;
    private final LotJpaRepository lots;
    private final MovementJpaRepository movements;

    public InventoryQueryService(
            ItemJpaRepository items,
            LotJpaRepository lots,
            MovementJpaRepository movements
    ) {
        this.items = items;
        this.lots = lots;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public ItemEntity item(String tenant, String id) {
        return items.findByTenantIdAndId(
                        required(tenant, "tenantId"),
                        required(id, "itemId")
                )
                .orElseThrow(() -> new IllegalArgumentException("item not found"));
    }

    @Transactional(readOnly = true)
    public List<ItemEntity> items(
            String tenant,
            String query,
            String category,
            int size
    ) {
        String normalizedTenant = required(tenant, "tenantId");
        String normalizedQuery = clean(query);
        PageRequest page = PageRequest.of(0, limit(size));

        if (category == null || category.isBlank()) {
            return items.search(
                    normalizedTenant,
                    normalizedQuery,
                    page
            ).getContent();
        }

        ItemCategoryValue categoryValue;
        try {
            categoryValue = ItemCategoryValue.parse(category);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "unsupported item category: " + category,
                    exception
            );
        }

        return items.searchByCategory(
                normalizedTenant,
                normalizedQuery,
                categoryValue,
                page
        ).getContent();
    }

    @Transactional(readOnly = true)
    public LotTrace trace(String tenant, String lotId, int size) {
        String normalizedTenant = required(tenant, "tenantId");
        String normalizedLot = required(lotId, "lotId");

        LotEntity lot = lots.findByTenantIdAndId(
                        normalizedTenant,
                        normalizedLot
                )
                .orElseThrow(() -> new IllegalArgumentException("lot not found"));

        List<MovementEntity> history = movements
                .findByTenantIdAndLotIdOrderByIdAsc(
                        normalizedTenant,
                        normalizedLot,
                        PageRequest.of(0, limit(size))
                );

        return new LotTrace(lot, history);
    }

    public record LotTrace(
            LotEntity lot,
            List<MovementEntity> movements
    ) {
    }

    private static int limit(int value) {
        return Math.max(1, Math.min(value <= 0 ? 50 : value, 200));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
