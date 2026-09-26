package com.htv.smartfarm.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA mapping of {@code fin_debt} (payable/receivable). Idempotent on
 * (tenant_id, idempotency_key). Outstanding is tracked in minor units and moves
 * toward zero as the debt is settled.
 */
@Entity
@Table(name = "fin_debt",
        uniqueConstraints = @UniqueConstraint(name = "uq_fin_debt_idem", columnNames = {"tenant_id", "idempotency_key"}),
        indexes = @Index(name = "ix_fin_debt_ref", columnList = "tenant_id,reference_id"))
public class DebtEntity {

    @Id
    @Column(name = "debt_id", length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "farm_id", nullable = false, length = 100)
    private String farmId;

    @Column(name = "counterparty_id", length = 100)
    private String counterpartyId;

    /** PAYABLE or RECEIVABLE. */
    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    /** OPEN, PARTIALLY_PAID, SETTLED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "principal_minor", nullable = false)
    private long principalMinor;

    @Column(name = "outstanding_minor", nullable = false)
    private long outstandingMinor;

    @Column(name = "due_at")
    private Long dueAt;

    @Column(name = "reference_id", length = 128)
    private String referenceId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected DebtEntity() {
    }

    public DebtEntity(String id, String tenantId, String farmId, String counterpartyId, String kind, String status,
                      String currencyCode, long principalMinor, long outstandingMinor, Long dueAt,
                      String referenceId, String idempotencyKey) {
        this.id = id;
        this.tenantId = tenantId;
        this.farmId = farmId;
        this.counterpartyId = counterpartyId;
        this.kind = kind;
        this.status = status;
        this.currencyCode = currencyCode;
        this.principalMinor = principalMinor;
        this.outstandingMinor = outstandingMinor;
        this.dueAt = dueAt;
        this.referenceId = referenceId;
        this.idempotencyKey = idempotencyKey;
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getFarmId() { return farmId; }
    public String getCounterpartyId() { return counterpartyId; }
    public String getKind() { return kind; }
    public String getStatus() { return status; }
    public String getCurrencyCode() { return currencyCode; }
    public long getPrincipalMinor() { return principalMinor; }
    public long getOutstandingMinor() { return outstandingMinor; }
    public Long getDueAt() { return dueAt; }
    public String getReferenceId() { return referenceId; }
    public String getIdempotencyKey() { return idempotencyKey; }

    public void applyPayment(long amountMinor) {
        long remaining = outstandingMinor - amountMinor;
        this.outstandingMinor = Math.max(0, remaining);
        this.status = this.outstandingMinor == 0 ? "SETTLED" : "PARTIALLY_PAID";
    }
}
