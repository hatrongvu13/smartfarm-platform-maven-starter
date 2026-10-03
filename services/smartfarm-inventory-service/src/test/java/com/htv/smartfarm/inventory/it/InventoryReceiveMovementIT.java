package com.htv.smartfarm.inventory.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.smartfarm.inventory.domain.InventoryCommands;
import com.htv.smartfarm.inventory.domain.InventoryRepository;
import com.htv.smartfarm.proto.common.v1.Quantity;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.inventory.v1.CreateItemRequest;
import com.htv.smartfarm.proto.inventory.v1.InventoryItem;
import com.htv.smartfarm.proto.inventory.v1.ItemCategory;
import com.htv.smartfarm.proto.inventory.v1.ReceiveStockRequest;
import com.htv.smartfarm.proto.inventory.v1.StockLot;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * V1 Inventory receive -> movement -> balance on REAL PostgreSQL (refactor §4). Proves the
 * conceptual path: Create Product -> Receive Stock -> Movement ledger -> Balance increases ->
 * Query, plus receive idempotency. Quantities use the project's decimal(18,3) Quantity type.
 */
class InventoryReceiveMovementIT extends AbstractInventoryPostgresIT {

    @Autowired
    private InventoryCommands inventory;

    // Unique per JVM run so repeated runs against a persistent external schema never collide
    // (Testcontainers gives a fresh DB; external mode reuses the schema).
    private static final String TENANT = "tenant-inv-it-" + Long.toString(System.nanoTime(), 36);
    private static final String WAREHOUSE = "wh-it-001";
    private static final String UNIT = "KG";

    private RequestContext ctx(String idemKey) {
        return RequestContext.newBuilder().setTenantId(TENANT).setActorId("it").setIdempotencyKey(idemKey).build();
    }

    private Quantity qty(String decimal) {
        return Quantity.newBuilder().setUnit(UNIT).setDecimalValue(decimal).build();
    }

    private String createFeedItem(String sku) {
        InventoryRepository.Item item = inventory.createItem(TENANT, CreateItemRequest.newBuilder()
                .setContext(ctx("item:" + sku))
                .setItem(InventoryItem.newBuilder()
                        .setSku(sku).setName("Starter Feed").setUnit(UNIT).setCategory(ItemCategory.ITEM_CATEGORY_FEED))
                .build());
        return item.id();
    }

    @Test
    void receiveIncreasesBalanceAndRecordsMovement() {
        String itemId = createFeedItem("FEED-IT-001");

        InventoryRepository.Movement m = inventory.receive(TENANT, ReceiveStockRequest.newBuilder()
                .setContext(ctx("recv:FEED-IT-001:1"))
                .setLot(StockLot.newBuilder().setItemId(itemId).setFarmId("farm-it").setWarehouseId(WAREHOUSE))
                .setQuantity(qty("40.000"))
                .setReferenceId("po-it-1")
                .build());

        // Movement ledger entry recorded as a RECEIPT.
        assertThat(m.kind()).isEqualTo("RECEIPT");
        assertThat(m.quantity()).isEqualByComparingTo("40.000");
        // Balance in real PostgreSQL reflects the receipt.
        assertThat(inventory.balance(TENANT, itemId, WAREHOUSE)).isEqualByComparingTo("40.000");

        // A second receipt on the same item/warehouse accumulates.
        inventory.receive(TENANT, ReceiveStockRequest.newBuilder()
                .setContext(ctx("recv:FEED-IT-001:2"))
                .setLot(StockLot.newBuilder().setItemId(itemId).setFarmId("farm-it").setWarehouseId(WAREHOUSE))
                .setQuantity(qty("10.000"))
                .setReferenceId("po-it-2")
                .build());
        assertThat(inventory.balance(TENANT, itemId, WAREHOUSE)).isEqualByComparingTo("50.000");
    }

    @Test
    void receiveIsIdempotentOnKey() {
        String itemId = createFeedItem("FEED-IT-002");
        // Idempotent receive requires a STABLE lot id: receive() generates a random lot id when
        // lot_id is blank, so a blank-lot retry would create a different lot and trip the
        // idempotency conflict guard. A real caller retrying a receipt supplies the same lot id.
        // (Finding: receive() is only key-idempotent when the caller pins lot_id — see report.)
        String lotId = "lot-it-fixed-002";
        ReceiveStockRequest req = ReceiveStockRequest.newBuilder()
                .setContext(ctx("recv:FEED-IT-002:once"))
                .setLot(StockLot.newBuilder().setLotId(lotId).setItemId(itemId).setFarmId("farm-it").setWarehouseId(WAREHOUSE))
                .setQuantity(qty("15.000"))
                .setReferenceId("po-it-3")
                .build();

        InventoryRepository.Movement first = inventory.receive(TENANT, req);
        InventoryRepository.Movement retry = inventory.receive(TENANT, req);

        assertThat(retry.id()).isEqualTo(first.id());
        // Balance increased only once despite two calls with the same idempotency key.
        assertThat(inventory.balance(TENANT, itemId, WAREHOUSE)).isEqualByComparingTo("15.000");
    }

    @Test
    void receiveWithConflictingIdempotencyKeyIsRejected() {
        String itemId = createFeedItem("FEED-IT-003");
        inventory.receive(TENANT, ReceiveStockRequest.newBuilder()
                .setContext(ctx("recv:FEED-IT-003:k"))
                .setLot(StockLot.newBuilder().setItemId(itemId).setFarmId("farm-it").setWarehouseId(WAREHOUSE))
                .setQuantity(qty("5.000")).setReferenceId("po-a").build());

        assertThatThrownBy(() -> inventory.receive(TENANT, ReceiveStockRequest.newBuilder()
                .setContext(ctx("recv:FEED-IT-003:k"))
                .setLot(StockLot.newBuilder().setItemId(itemId).setFarmId("farm-it").setWarehouseId(WAREHOUSE))
                .setQuantity(qty("9.000")).setReferenceId("po-b").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency");
    }
}
