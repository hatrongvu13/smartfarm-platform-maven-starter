package com.htv.smartfarm.inventory.domain.stock;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * JPA mapping of {@code inv_balance} (composite key tenant_id + lot_id).
 */
@Entity
@Table(name = "inv_balance")
@IdClass(BalanceId.class)
public class BalanceEntity {

    @Id
    @Column(name = "tenant_id", length = 100)
    private String tenantId;

    @Id
    @Column(name = "lot_id", length = 36)
    private String lotId;

    @Column(name = "on_hand", nullable = false, precision = 18, scale = 3)
    private BigDecimal onHand;

    @Column(name = "reserved", nullable = false, precision = 18, scale = 3)
    private BigDecimal reserved;

    protected BalanceEntity() {
    }

    public BalanceEntity(String tenantId, String lotId, BigDecimal onHand) {
        this.tenantId = tenantId;
        this.lotId = lotId;
        this.onHand = onHand;
        this.reserved = BigDecimal.ZERO;
    }

    public BigDecimal getOnHand() {
        return onHand;
    }

    public BigDecimal getReserved() {
        return reserved;
    }
}
