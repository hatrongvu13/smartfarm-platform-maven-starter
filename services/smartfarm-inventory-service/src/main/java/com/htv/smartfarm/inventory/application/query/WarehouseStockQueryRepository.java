package com.htv.smartfarm.inventory.application.query;

import java.util.*;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import com.htv.smartfarm.inventory.domain.stock.BalanceEntity;
import com.htv.smartfarm.inventory.domain.stock.BalanceId;

public interface WarehouseStockQueryRepository extends Repository<BalanceEntity, BalanceId> {
    @Query("select new com.htv.smartfarm.inventory.application.query.WarehouseStockView(l.warehouseId,i.id,i.sku,i.name,i.category,i.unit,sum(b.onHand),sum(b.reserved),cast(i.reorderThreshold as big_decimal),count(l.id)) from BalanceEntity b,LotEntity l,ItemEntity i where b.tenantId=:tenant and l.tenantId=b.tenantId and l.id=b.lotId and i.tenantId=l.tenantId and i.id=l.itemId and l.warehouseId=:warehouse and (:q='' or lower(i.sku) like lower(concat('%',:q,'%')) or lower(i.name) like lower(concat('%',:q,'%'))) group by l.warehouseId,i.id,i.sku,i.name,i.category,i.unit,i.reorderThreshold order by i.sku,i.id")
    List<WarehouseStockView> list(@Param("tenant") String tenant, @Param("warehouse") String warehouse, @Param("q") String q, Pageable page);
}
