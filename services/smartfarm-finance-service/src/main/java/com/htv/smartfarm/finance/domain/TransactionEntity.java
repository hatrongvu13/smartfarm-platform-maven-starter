package com.htv.smartfarm.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA mapping of {@code fin_transaction} (ledger line). The (tenant_id, idempotency_key)
 * unique constraint enforces idempotent posting so a retried saga step never double-posts.
 * Amounts are stored as integer minor units + currency to avoid floating-point drift,
 * matching the {@code smartfarm.common.v1.Money} contract.
 */
@Entity
@Table(name = "fin_transaction",
        uniqueConstraints = @UniqueConstraint(name = "uq_fin_txn_idem", columnNames = {"tenant_id", "idempotency_key"}),
        indexes = @Index(name = "ix_fin_txn_batch", columnList = "tenant_id,batch_id"))
public class TransactionEntity {

    @Id
    @Column(name = "transaction_id", length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "farm_id", nullable = false, length = 100)
    private String farmId;

    @Column(name = "batch_id", length = 100)
    private String batchId;

    /** INCOME or EXPENSE. */
    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "minor_units", nullable = false)
    private long minorUnits;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private long occurredAt;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected TransactionEntity() {
    }

    public TransactionEntity(String id, String tenantId, String farmId, String batchId, String kind,
                             String currencyCode, long minorUnits, String category, String referenceType,
                             String referenceId, String description, long occurredAt, String idempotencyKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.farmId = farmId;
        this.batchId = batchId;
        this.kind = kind;
        this.currencyCode = currencyCode;
        this.minorUnits = minorUnits;
        this.category = category;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.description = description;
        this.occurredAt = occurredAt;
        this.idempotencyKey = idempotencyKey;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getFarmId() { return farmId; }
    public String getBatchId() { return batchId; }
    public String getKind() { return kind; }
    public String getCurrencyCode() { return currencyCode; }
    public long getMinorUnits() { return minorUnits; }
    public String getCategory() { return category; }
    public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public String getDescription() { return description; }
    public long getOccurredAt() { return occurredAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
