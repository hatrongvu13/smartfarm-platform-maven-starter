package com.htv.smartfarm.finance.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.htv.smartfarm.finance.domain.FinanceCommands;
import com.htv.smartfarm.finance.domain.TransactionEntity;
import com.htv.smartfarm.finance.domain.TransactionJpaRepository;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.finance.v1.FinanceTransaction;
import com.htv.smartfarm.proto.finance.v1.RecordExpenseRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * V1 Finance idempotency on REAL PostgreSQL (refactor §4). Proves the (tenant, idempotency_key)
 * guard: a retried expense with the same key returns the SAME transaction (no double-post), and a
 * reused key with a DIFFERENT amount is rejected. Money is integer minor units (no floating point).
 */
class FinanceIdempotencyIT extends AbstractFinancePostgresIT {

    @Autowired
    private FinanceCommands finance;
    @Autowired
    private TransactionJpaRepository transactions;

    private static final String TENANT = "tenant-it-001";

    private RecordExpenseRequest expense(String idemKey, long minor, String currency) {
        return RecordExpenseRequest.newBuilder()
                .setContext(RequestContext.newBuilder()
                        .setTenantId(TENANT).setActorId("it").setIdempotencyKey(idemKey))
                .setTransaction(FinanceTransaction.newBuilder()
                        .setFarmId("farm-it-001")
                        .setAmount(Money.newBuilder().setCurrencyCode(currency).setMinorUnits(minor))
                        .setCategory("FEED"))
                .build();
    }

    @Test
    void retriedExpenseWithSameKeyReturnsSameTransactionAndPersistsOnce() {
        String key = "order-it-001:finance";
        TransactionEntity first = finance.recordExpense(TENANT, expense(key, 125_00, "VND"));
        TransactionEntity retry = finance.recordExpense(TENANT, expense(key, 125_00, "VND"));

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(retry.getMinorUnits()).isEqualTo(125_00);
        assertThat(retry.getCurrencyCode()).isEqualTo("VND");
        // Persisted exactly once in real PostgreSQL.
        assertThat(transactions.findByTenantIdAndIdempotencyKey(TENANT, key)).isPresent();
        long count = transactions.findAll().stream()
                .filter(t -> key.equals(t.getIdempotencyKey()) && TENANT.equals(t.getTenantId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void reusedKeyWithDifferentAmountIsRejected() {
        String key = "order-it-002:finance";
        finance.recordExpense(TENANT, expense(key, 100_00, "VND"));
        assertThatThrownBy(() -> finance.recordExpense(TENANT, expense(key, 999_00, "VND")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency key reused");
    }
}
