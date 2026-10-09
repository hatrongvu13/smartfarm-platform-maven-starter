package com.htv.smartfarm.inventory.domain.stock;

import com.htv.smartfarm.inventory.domain.lot.LotEntity;
import com.htv.smartfarm.inventory.domain.item.ItemEntity;
import com.htv.smartfarm.inventory.domain.lot.LotEntity;
import com.htv.smartfarm.inventory.domain.item.ItemEntity;
import com.htv.smartfarm.inventory.domain.lot.LotEntity;
import com.htv.smartfarm.inventory.domain.item.ItemEntity;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA access to {@link BalanceEntity}. The increment/decrement are
 * atomic conditional UPDATEs (row count == 1 signals success), preserving the
 * {@code on_hand >= delta} guard from the original JDBC decrement so stock never
 * goes negative under concurrent issue.
 */
public interface BalanceJpaRepository extends JpaRepository<BalanceEntity, BalanceId> {

    @Modifying
    @Query("update BalanceEntity b set b.onHand = b.onHand + :delta "
            + "where b.tenantId = :tenant and b.lotId = :lot")
    int increment(@Param("tenant") String tenant, @Param("lot") String lot, @Param("delta") BigDecimal delta);

    @Modifying
    @Query("update BalanceEntity b set b.onHand = b.onHand - :delta "
            + "where b.tenantId = :tenant and b.lotId = :lot and b.onHand >= :delta")
    int decrement(@Param("tenant") String tenant, @Param("lot") String lot, @Param("delta") BigDecimal delta);

    /**
     * Reserve: only succeeds when AVAILABLE (on_hand - reserved) covers the quantity. Atomic guard.
     */
    @Modifying
    @Query("update BalanceEntity b set b.reserved = b.reserved + :delta "
            + "where b.tenantId = :tenant and b.lotId = :lot and (b.onHand - b.reserved) >= :delta")
    int reserve(@Param("tenant") String tenant, @Param("lot") String lot, @Param("delta") BigDecimal delta);

    /**
     * Release a reservation (compensating action): give the reserved quantity back to available.
     */
    @Modifying
    @Query("update BalanceEntity b set b.reserved = b.reserved - :delta "
            + "where b.tenantId = :tenant and b.lotId = :lot and b.reserved >= :delta")
    int release(@Param("tenant") String tenant, @Param("lot") String lot, @Param("delta") BigDecimal delta);

    /**
     * Commit a reservation: consume the reserved stock (on_hand and reserved both drop).
     */
    @Modifying
    @Query("update BalanceEntity b set b.onHand = b.onHand - :delta, b.reserved = b.reserved - :delta "
            + "where b.tenantId = :tenant and b.lotId = :lot and b.reserved >= :delta and b.onHand >= :delta")
    int commit(@Param("tenant") String tenant, @Param("lot") String lot, @Param("delta") BigDecimal delta);

    @Query("select coalesce(sum(b.onHand), 0) from BalanceEntity b, LotEntity l "
            + "where l.id = b.lotId and l.tenantId = b.tenantId "
            + "and b.tenantId = :tenant and l.itemId = :item and l.warehouseId = :warehouse")
    BigDecimal sumBalance(@Param("tenant") String tenant, @Param("item") String item,
                          @Param("warehouse") String warehouse);

    @Query("select coalesce(sum(b.reserved), 0) from BalanceEntity b, LotEntity l "
            + "where l.id = b.lotId and l.tenantId = b.tenantId "
            + "and b.tenantId = :tenant and l.itemId = :item and l.warehouseId = :warehouse")
    BigDecimal sumReserved(@Param("tenant") String tenant, @Param("item") String item,
                           @Param("warehouse") String warehouse);

    /**
     * Lots for an item in a warehouse that still have available stock, oldest first (FIFO).
     */
    @Query("select b.lotId from BalanceEntity b, LotEntity l "
            + "where l.id = b.lotId and l.tenantId = b.tenantId "
            + "and b.tenantId = :tenant and l.itemId = :item and l.warehouseId = :warehouse "
            + "and (b.onHand - b.reserved) > 0 order by b.lotId")
    java.util.List<String> lotsWithAvailable(@Param("tenant") String tenant, @Param("item") String item,
                                             @Param("warehouse") String warehouse);

    /**
     * Aggregated balance per (item, warehouse) for a farm, with the item's unit and reorder
     * threshold. Returns [itemId, warehouseId, unit, sum(onHand), sum(reserved), reorderThreshold].
     * The threshold comparison (available < threshold, threshold > 0) is done in Java to avoid
     * a JPQL numeric CAST of the string-typed threshold column.
     */
    @Query("select l.itemId, l.warehouseId, i.unit, "
            + "sum(b.onHand), sum(b.reserved), i.reorderThreshold "
            + "from BalanceEntity b, LotEntity l, ItemEntity i "
            + "where l.id = b.lotId and l.tenantId = b.tenantId "
            + "and i.id = l.itemId and i.tenantId = l.tenantId "
            + "and b.tenantId = :tenant and l.farmId = :farm "
            + "group by l.itemId, l.warehouseId, i.unit, i.reorderThreshold "
            + "order by l.itemId, l.warehouseId")
    java.util.List<Object[]> balancesByFarm(@Param("tenant") String tenant, @Param("farm") String farm);
}
