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
    public void recordPayable(RecordPayableRequest req, StreamObserver<DebtResponse> out) {
        respond(out, () -> toDebt(commands.recordPayable(tenant(), req)));
    }

    @Override
    public void settleDebt(SettleDebtRequest req, StreamObserver<DebtResponse> out) {
        respond(out, () -> toDebt(commands.settleDebt(tenant(), req)));
    }
}
