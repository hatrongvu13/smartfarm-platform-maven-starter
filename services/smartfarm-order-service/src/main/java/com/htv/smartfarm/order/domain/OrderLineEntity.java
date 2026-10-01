package com.htv.smartfarm.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One line of a multi-line order. Each line reserves its own stock and, on success, is committed
 * (or released on compensation) independently — so {@code reservationId} is tracked per line.
 * Ordered within an order by {@code lineNo} for deterministic reserve/commit sequencing.
 */
@Entity
@Table(name = "ord_order_line",
        indexes = @Index(name = "ix_ordline_order", columnList = "order_id,line_no"))
public class OrderLineEntity {

    @Id
    @Column(name = "line_id", length = 36)
    private String id;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_id", nullable = false, length = 36)
    private String itemId;

    @Column(name = "warehouse_id", nullable = false, length = 100)
    private String warehouseId;

    @Column(name = "quantity", nullable = false, length = 40)
    private String quantity;

    @Column(name = "quantity_unit", nullable = false, length = 20)
    private String quantityUnit;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(name = "line_total_minor", nullable = false)
    private long lineTotalMinor;

    /** Reservation held for this line; filled once reserve succeeds, cleared/kept for compensation. */
    @Column(name = "reservation_id", length = 36)
    private String reservationId;

    @Version
    @Column(name = "row_version", nullable = false)
    private long version;

    protected OrderLineEntity() {
    }

    public OrderLineEntity(String id, String orderId, int lineNo, String itemId, String warehouseId,
                           String quantity, String quantityUnit, long unitPriceMinor, long lineTotalMinor) {
        this.id = id;
        this.orderId = orderId;
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.quantityUnit = quantityUnit;
        this.unitPriceMinor = unitPriceMinor;
        this.lineTotalMinor = lineTotalMinor;
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public int getLineNo() { return lineNo; }
    public String getItemId() { return itemId; }
    public String getWarehouseId() { return warehouseId; }
    public String getQuantity() { return quantity; }
    public String getQuantityUnit() { return quantityUnit; }
    public long getUnitPriceMinor() { return unitPriceMinor; }
    public long getLineTotalMinor() { return lineTotalMinor; }
    public String getReservationId() { return reservationId; }
    public long getVersion() { return version; }

    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
}
