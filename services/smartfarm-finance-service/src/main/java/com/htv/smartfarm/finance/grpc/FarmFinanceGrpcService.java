package com.htv.smartfarm.finance.grpc;

import com.htv.smartfarm.finance.domain.DebtEntity;
import com.htv.smartfarm.finance.domain.FinanceCommands;
import com.htv.smartfarm.finance.domain.TransactionEntity;
import com.htv.smartfarm.proto.common.v1.Money;
import com.htv.smartfarm.proto.finance.v1.*;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;

import java.util.function.Supplier;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

/**
 * gRPC surface for farm finance. Internal service — reachable only via gRPC behind the
 * gateway/service-token boundary (Phase 1/2). Tenant is taken from the verified call
 * context, never from the request body.
 */
@Service
public class FarmFinanceGrpcService extends FarmFinanceServiceGrpc.FarmFinanceServiceImplBase {

    private final FinanceCommands commands;

    public FarmFinanceGrpcService(FinanceCommands commands) {
        this.commands = commands;
    }

    private static String tenant() {
        String t = GrpcSecurityContext.TENANT.get();
        if (t == null || t.isBlank()) throw Status.UNAUTHENTICATED.asRuntimeException();
        return t;
    }

    private static <T> void respond(StreamObserver<T> out, Supplier<T> action) {
        try {
            out.onNext(action.get());
            out.onCompleted();
        } catch (IllegalArgumentException e) {
            out.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            out.onError(Status.ALREADY_EXISTS.withDescription("duplicate").asRuntimeException());
        } catch (RuntimeException e) {
            out.onError(Status.INTERNAL.withDescription("finance operation failed").asRuntimeException());
        }
    }

    private static Money money(String currency, long minor) {
        return Money.newBuilder().setCurrencyCode(currency).setMinorUnits(minor).build();
    }

    private static TransactionResponse toTxn(TransactionEntity e) {
        var b = FinanceTransaction.newBuilder()
                .setTransactionId(e.getId()).setFarmId(e.getFarmId())
                .setKind("EXPENSE".equals(e.getKind()) ? TransactionKind.TRANSACTION_KIND_EXPENSE : TransactionKind.TRANSACTION_KIND_INCOME)
                .setAmount(money(e.getCurrencyCode(), e.getMinorUnits()));
        if (e.getBatchId() != null) b.setBatchId(e.getBatchId());
        if (e.getCategory() != null) b.setCategory(e.getCategory());
        if (e.getReferenceType() != null) b.setReferenceType(e.getReferenceType());
        if (e.getReferenceId() != null) b.setReferenceId(e.getReferenceId());
        if (e.getDescription() != null) b.setDescription(e.getDescription());
        return TransactionResponse.newBuilder().setTransaction(b).build();
    }

    private static DebtResponse toDebt(DebtEntity e) {
        var status = switch (e.getStatus()) {
            case "SETTLED" -> DebtStatus.DEBT_STATUS_SETTLED;
            case "PARTIALLY_PAID" -> DebtStatus.DEBT_STATUS_PARTIALLY_PAID;
            default -> DebtStatus.DEBT_STATUS_OPEN;
        };
        var b = Debt.newBuilder()
                .setDebtId(e.getId()).setFarmId(e.getFarmId())
                .setKind("PAYABLE".equals(e.getKind()) ? DebtKind.DEBT_KIND_PAYABLE : DebtKind.DEBT_KIND_RECEIVABLE)
                .setStatus(status)
                .setPrincipal(money(e.getCurrencyCode(), e.getPrincipalMinor()))
                .setOutstanding(money(e.getCurrencyCode(), e.getOutstandingMinor()));
        if (e.getCounterpartyId() != null) b.setCounterpartyId(e.getCounterpartyId());
        if (e.getReferenceId() != null) b.setReferenceId(e.getReferenceId());
        return DebtResponse.newBuilder().setDebt(b).build();
    }

    @Override
    public void recordExpense(RecordExpenseRequest req, StreamObserver<TransactionResponse> out) {
        respond(out, () -> toTxn(commands.recordExpense(tenant(), req)));
    }

    @Override
    public void reverseExpense(ReverseExpenseRequest req, StreamObserver<TransactionResponse> out) {
        respond(out, () -> {
            if (req.getOriginalExpenseKey().isBlank() || req.getReversalKey().isBlank())
                throw new IllegalArgumentException("original_expense_key and reversal_key required");
            return toTxn(commands.reverseExpense(tenant(), req.getOriginalExpenseKey(), req.getReversalKey()));
        });
    }

    @Override
    public void recordPayable(RecordPayableRequest req, StreamObserver<DebtResponse> out) {
        respond(out, () -> toDebt(commands.recordPayable(tenant(), req)));
    }

    @Override
    public void settleDebt(SettleDebtRequest req, StreamObserver<DebtResponse> out) {
        respond(out, () -> toDebt(commands.settleDebt(tenant(), req)));
    }

    @Override
    public void recordIncome(RecordIncomeRequest req, StreamObserver<TransactionResponse> out) {
        respond(out, () -> toTxn(commands.recordIncome(tenant(), req)));
    }

    @Override
    public void recordReceivable(RecordReceivableRequest req, StreamObserver<DebtResponse> out) {
        respond(out, () -> toDebt(commands.recordReceivable(tenant(), req)));
    }

    @Override
    public void getTransaction(GetTransactionRequest req, StreamObserver<TransactionResponse> out) {
        respond(out, () -> {
            var t = commands.getTransaction(tenant(), req.getTransactionId());
            if (t == null) throw Status.NOT_FOUND.withDescription("transaction not found").asRuntimeException();
            return toTxn(t);
        });
    }

    @Override
    public void listTransactions(ListTransactionsRequest req, StreamObserver<ListTransactionsResponse> out) {
        respond(out, () -> {
            int limit = req.hasPage() && req.getPage().getPageSize() > 0 ? req.getPage().getPageSize() : 100;
            Long from = req.hasPeriod() && req.getPeriod().hasFrom() ? req.getPeriod().getFrom().getSeconds() * 1000 : null;
            Long to = req.hasPeriod() && req.getPeriod().hasTo() ? req.getPeriod().getTo().getSeconds() * 1000 : null;
            var b = ListTransactionsResponse.newBuilder();
            for (var e : commands.listTransactions(tenant(), req.getFarmId(), req.getBatchId(), from, to, limit))
                b.addTransactions(toTxn(e).getTransaction());
            return b.build();
        });
    }

    @Override
    public void getBatchCost(GetBatchCostRequest req, StreamObserver<BatchCostResponse> out) {
        respond(out, () -> {
            var v = commands.batchCost(tenant(), req.getFarmId(), req.getBatchId());
            return BatchCostResponse.newBuilder().setCost(BatchCost.newBuilder()
                    .setFarmId(v.farmId()).setBatchId(v.batchId())
                    .setFeedCost(money(v.currency(), v.feedMinor()))
                    .setMedicineCost(money(v.currency(), v.medicineMinor()))
                    .setLaborCost(money(v.currency(), v.laborMinor()))
                    .setOtherCost(money(v.currency(), v.otherMinor()))
                    .setTotalCost(money(v.currency(), v.totalMinor()))).build();
        });
    }

    @Override
    public void getCashFlow(GetCashFlowRequest req, StreamObserver<CashFlowResponse> out) {
        respond(out, () -> {
            Long from = req.hasPeriod() && req.getPeriod().hasFrom() ? req.getPeriod().getFrom().getSeconds() * 1000 : null;
            Long to = req.hasPeriod() && req.getPeriod().hasTo() ? req.getPeriod().getTo().getSeconds() * 1000 : null;
            var v = commands.cashFlow(tenant(), req.getFarmId(), from, to);
            return CashFlowResponse.newBuilder().setCashFlow(CashFlow.newBuilder()
                    .setFarmId(v.farmId())
                    .setInflow(money(v.currency(), v.inflowMinor()))
                    .setOutflow(money(v.currency(), v.outflowMinor()))
                    .setNet(money(v.currency(), v.netMinor()))).build();
        });
    }
}
