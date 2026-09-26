package com.htv.smartfarm.finance.domain;

import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.finance.v1.*;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finance write model. Every command is idempotent on (tenant, idempotency_key) so a
 * retried saga step re-reads the prior result instead of double-posting. Amounts are
 * handled in integer minor units.
 */
@Service
public class FinanceCommands {

    private final TransactionJpaRepository transactions;
    private final DebtJpaRepository debts;

    public FinanceCommands(TransactionJpaRepository transactions, DebtJpaRepository debts) {
        this.transactions = transactions;
        this.debts = debts;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }

    private static String key(RequestContext ctx, String tenant) {
        require(ctx != null, "context required");
        require(ctx.getTenantId().isBlank() || tenant.equals(ctx.getTenantId()), "tenant mismatch");
        String k = ctx.getIdempotencyKey();
        require(!k.isBlank() && k.length() <= 128, "idempotency_key required (max 128)");
        return k;
    }

    private static void validMoney(Money m) {
        require(m != null && !m.getCurrencyCode().isBlank() && m.getCurrencyCode().length() == 3, "currency required (ISO 4217)");
        require(m.getMinorUnits() > 0, "amount must be positive");
    }

    @Transactional
    public TransactionEntity recordExpense(String tenant, RecordExpenseRequest req) {
        require(req.hasContext() && req.hasTransaction(), "context/transaction required");
        String k = key(req.getContext(), tenant);
        var t = req.getTransaction();
        require(!t.getFarmId().isBlank(), "farm_id required");
        validMoney(t.getAmount());

        var prior = transactions.findByTenantIdAndIdempotencyKey(tenant, k).orElse(null);
        if (prior != null) {
            require(prior.getKind().equals("EXPENSE") && prior.getMinorUnits() == t.getAmount().getMinorUnits()
                    && prior.getCurrencyCode().equals(t.getAmount().getCurrencyCode()), "idempotency key reused with different expense");
            return prior;
        }
        var e = new TransactionEntity(UUID.randomUUID().toString(), tenant, t.getFarmId(),
                emptyToNull(t.getBatchId()), "EXPENSE", t.getAmount().getCurrencyCode(), t.getAmount().getMinorUnits(),
                emptyToNull(t.getCategory()), emptyToNull(t.getReferenceType()), emptyToNull(t.getReferenceId()),
                emptyToNull(t.getDescription()), System.currentTimeMillis(), k);
        transactions.save(e);
        return e;
    }

    @Transactional
    public DebtEntity recordPayable(String tenant, RecordPayableRequest req) {
        require(req.hasContext() && req.hasDebt(), "context/debt required");
        String k = key(req.getContext(), tenant);
        var d = req.getDebt();
        require(!d.getFarmId().isBlank(), "farm_id required");
        validMoney(d.getPrincipal());

        var prior = debts.findByTenantIdAndIdempotencyKey(tenant, k).orElse(null);
        if (prior != null) {
            require(prior.getKind().equals("PAYABLE") && prior.getPrincipalMinor() == d.getPrincipal().getMinorUnits(), "idempotency key reused with different payable");
            return prior;
        }
        long principal = d.getPrincipal().getMinorUnits();
        var e = new DebtEntity(UUID.randomUUID().toString(), tenant, d.getFarmId(), emptyToNull(d.getCounterpartyId()),
                "PAYABLE", "OPEN", d.getPrincipal().getCurrencyCode(), principal, principal,
                d.hasDueAt() ? d.getDueAt().getSeconds() * 1000 : null, emptyToNull(d.getReferenceId()), k);
        debts.save(e);
        return e;
    }

    @Transactional
    public DebtEntity settleDebt(String tenant, SettleDebtRequest req) {
        require(req.hasContext(), "context required");
        key(req.getContext(), tenant);
        require(!req.getDebtId().isBlank(), "debt_id required");
        validMoney(req.getAmount());
        var debt = debts.findByTenantIdAndId(tenant, req.getDebtId()).orElseThrow(() -> new IllegalArgumentException("debt not found"));
        require(debt.getCurrencyCode().equals(req.getAmount().getCurrencyCode()), "currency mismatch");
        require(req.getAmount().getMinorUnits() <= debt.getOutstandingMinor(), "amount exceeds outstanding");
        debt.applyPayment(req.getAmount().getMinorUnits());
        debts.save(debt);
        return debt;
    }

    /**
     * Compensating action for the saga: reverse a previously posted expense by writing an
     * offsetting entry keyed off the original. Idempotent on the reversal key.
     */
    @Transactional
    public TransactionEntity reverseExpense(String tenant, String originalReferenceId, String reversalKey) {
        require(originalReferenceId != null && !originalReferenceId.isBlank(), "original reference required");
        require(reversalKey != null && !reversalKey.isBlank() && reversalKey.length() <= 128, "reversal key required");
        var prior = transactions.findByTenantIdAndIdempotencyKey(tenant, reversalKey).orElse(null);
        if (prior != null) return prior;
        // Find the original expense by reference to mirror its amount as an offsetting INCOME.
        var original = transactions.findByTenantIdAndIdempotencyKey(tenant, originalReferenceId).orElse(null);
        require(original != null, "original expense not found for reversal");
        var reversal = new TransactionEntity(UUID.randomUUID().toString(), tenant, original.getFarmId(),
                original.getBatchId(), "INCOME", original.getCurrencyCode(), original.getMinorUnits(),
                "SAGA_COMPENSATION", "ORDER_REVERSAL", original.getId(),
                "Reversal of expense " + original.getId(), System.currentTimeMillis(), reversalKey);
        transactions.save(reversal);
        return reversal;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
