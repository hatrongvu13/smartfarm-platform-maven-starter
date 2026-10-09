package com.htv.smartfarm.inventory.grpc;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.inventory.application.query.WarehouseStockView;
import com.htv.smartfarm.inventory.domain.warehouse.WarehouseEntity;
import com.htv.smartfarm.proto.common.v1.Quantity;
import com.htv.smartfarm.proto.inventory.v1.*;

final class WarehouseProtoMapper {
    private WarehouseProtoMapper() {
    }

    static Warehouse proto(WarehouseEntity v) {
        var b = Warehouse.newBuilder().setWarehouseId(v.getId()).setFarmId(v.getFarmId()).setCode(v.getCode()).setName(v.getName()).setStatus(com.htv.smartfarm.proto.inventory.v1.WarehouseStatus.valueOf("WAREHOUSE_STATUS_" + v.getStatus().name())).setCreatedAt(ts(v.getCreatedAt())).setUpdatedAt(ts(v.getUpdatedAt())).setVersion(v.getVersion());
        if (v.getDescription() != null) b.setDescription(v.getDescription());
        if (v.getAddress() != null) b.setAddress(v.getAddress());
        return b.build();
    }

    static WarehouseStockItem item(WarehouseStockView v) {
        return WarehouseStockItem.newBuilder().setWarehouseId(v.warehouseId()).setItemId(v.itemId()).setSku(v.sku()).setItemName(v.itemName()).setCategory(ItemCategory.valueOf(v.category())).setUnit(v.unit()).setOnHand(q(v.onHand(), v.unit())).setReserved(q(v.reserved(), v.unit())).setAvailable(q(v.available(), v.unit())).setReorderThreshold(q(v.reorderThreshold(), v.unit())).setLowStock(v.lowStock()).setLotCount(v.lotCount()).build();
    }

    private static Quantity q(java.math.BigDecimal n, String u) {
        return Quantity.newBuilder().setDecimalValue(n.toPlainString()).setUnit(u).build();
    }

    private static Timestamp ts(long m) {
        return Timestamp.newBuilder().setSeconds(m / 1000).setNanos((int) (m % 1000) * 1000000).build();
    }
}
