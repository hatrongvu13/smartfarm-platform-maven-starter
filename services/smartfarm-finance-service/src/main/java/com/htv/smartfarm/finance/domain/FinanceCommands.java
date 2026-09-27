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
    public TransactionEntity recordIncome(String tenant, RecordIncomeRequest req) {
        require(req.hasContext() && req.hasTransaction(), "context/transaction required");
        String k = key(req.getContext(), tenant);
        var t = req.getTransaction();
        require(!t.getFarmId().isBlank(), "farm_id required");
        validMoney(t.getAmount());
        var prior = transactions.findByTenantIdAndIdempotencyKey(tenant, k).orElse(null);
        if (prior != null) {
            require(prior.getKind().equals("INCOME") && prior.getMinorUnits() == t.getAmount().getMinorUnits()
                    && prior.getCurrencyCode().equals(t.getAmount().getCurrencyCode()), "idempotency key reused with different income");
            return prior;
        }
        var e = new TransactionEntity(UUID.randomUUID().toString(), tenant, t.getFarmId(),
                emptyToNull(t.getBatchId()), "INCOME", t.getAmount().getCurrencyCode(), t.getAmount().getMinorUnits(),
                emptyToNull(t.getCategory()), emptyToNull(t.getReferenceType()), emptyToNull(t.getReferenceId()),
                emptyToNull(t.getDescription()), System.currentTimeMillis(), k);
        transactions.save(e);
        return e;
    }

    @Transactional(readOnly = true)
    public TransactionEntity getTransaction(String tenant, String id) {
        return transactions.findByTenantIdAndId(tenant, id).orElse(null);
    }

    @Transactional(readOnly = true)
    public java.util.List<TransactionEntity> listTransactions(String tenant, String farm, String batch, Long from, Long to, int limit) {
        require(farm != null && !farm.isBlank(), "farm_id required");
        return transactions.list(tenant, farm, emptyToNull(batch), from, to,
                org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500))));
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
    public DebtEntity recordReceivable(String tenant, RecordReceivableRequest req) {
        require(req.hasContext() && req.hasDebt(), "context/debt required");
        String k = key(req.getContext(), tenant);
        var d = req.getDebt();
        require(!d.getFarmId().isBlank(), "farm_id required");
        validMoney(d.getPrincipal());
        var prior = debts.findByTenantIdAndIdempotencyKey(tenant, k).orElse(null);
        if (prior != null) {
            require(prior.getKind().equals("RECEIVABLE") && prior.getPrincipalMinor() == d.getPrincipal().getMinorUnits(), "idempotency key reused with different receivable");
            return prior;
        }
        long principal = d.getPrincipal().getMinorUnits();
        var e = new DebtEntity(UUID.randomUUID().toString(), tenant, d.getFarmId(), emptyToNull(d.getCounterpartyId()),
                "RECEIVABLE", "OPEN", d.getPrincipal().getCurrencyCode(), principal, principal,
                d.hasDueAt() ? d.getDueAt().getSeconds() * 1000 : null, emptyToNull(d.getReferenceId()), k);
        debts.save(e);
        return e;
    }

    /** Batch cost breakdown: sum EXPENSE by category (FEED/MEDICINE/LABOR/other) for one batch. */
    @Transactional(readOnly = true)
    public BatchCostView batchCost(String tenant, String farm, String batch) {
        require(farm != null && !farm.isBlank() && batch != null && !batch.isBlank(), "farm_id and batch_id required");
        long feed = transactions.sumByKindAndCategory(tenant, farm, batch, "EXPENSE", "FEED");
        long medicine = transactions.sumByKindAndCategory(tenant, farm, batch, "EXPENSE", "MEDICINE");
        long labor = transactions.sumByKindAndCategory(tenant, farm, batch, "EXPENSE", "LABOR");
        long all = transactions.sumByKindAndCategory(tenant, farm, batch, "EXPENSE", null);
        long other = all - feed - medicine - labor;
        return new BatchCostView(farm, batch, currency(tenant, farm), feed, medicine, labor, Math.max(0, other), all);
    }

    /** Cash flow: inflow (INCOME) vs outflow (EXPENSE) over an optional period. */
    @Transactional(readOnly = true)
    public CashFlowView cashFlow(String tenant, String farm, Long from, Long to) {
        require(farm != null && !farm.isBlank(), "farm_id required");
        long inflow = transactions.sumByKind(tenant, farm, "INCOME", from, to);
        long outflow = transactions.sumByKind(tenant, farm, "EXPENSE", from, to);
        return new CashFlowView(farm, currency(tenant, farm), inflow, outflow, inflow - outflow, from, to);
    }

    private String currency(String tenant, String farm) {
        var list = transactions.currencies(tenant, farm, org.springframework.data.domain.PageRequest.of(0, 1));
        return list.isEmpty() ? "VND" : list.get(0);
    }

    /** Aggregate views (currency + minor units). */
    public record BatchCostView(String farmId, String batchId, String currency,
                                long feedMinor, long medicineMinor, long laborMinor, long otherMinor, long totalMinor) {
    }

    public record CashFlowView(String farmId, String currency, long inflowMinor, long outflowMinor,
                               long netMinor, Long fromMs, Long toMs) {
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
