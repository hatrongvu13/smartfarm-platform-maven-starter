package com.htv.smartfarm.order.grpc;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.order.saga.administration.OrderSagaAdministrationService;
import com.htv.smartfarm.order.saga.administration.OrderSagaInspection;
import com.htv.smartfarm.proto.order.v1.*;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class OrderSagaAdministrationGrpcService extends
        OrderSagaAdministrationServiceGrpc.OrderSagaAdministrationServiceImplBase {
    private final OrderSagaAdministrationService administration;
    public OrderSagaAdministrationGrpcService(OrderSagaAdministrationService administration) {
        this.administration = administration;
    }

    @Override public void getOrderSaga(GetOrderSagaRequest request, StreamObserver<OrderSagaResponse> out) {
        execute(out, () -> administration.inspect(tenant(), required(request.getSagaId(), "saga_id")));
    }
    @Override public void retryOrderSagaStep(RetryOrderSagaStepRequest request, StreamObserver<OrderSagaResponse> out) {
        execute(out, () -> administration.retryStep(tenant(), required(request.getSagaId(), "saga_id"),
                required(request.getStepKey(), "step_key"), actor(), required(request.getReason(), "reason")));
    }
    @Override public void resumeOrderSaga(OrderSagaActionRequest request, StreamObserver<OrderSagaResponse> out) {
        execute(out, () -> administration.resumeSaga(tenant(), required(request.getSagaId(), "saga_id"), actor(), required(request.getReason(), "reason")));
    }
    @Override public void forceCompensateOrder(OrderSagaActionRequest request, StreamObserver<OrderSagaResponse> out) {
        execute(out, () -> administration.forceCompensate(tenant(), required(request.getSagaId(), "saga_id"), actor(), required(request.getReason(), "reason")));
    }
    @Override public void forceCancelOrder(OrderSagaActionRequest request, StreamObserver<OrderSagaResponse> out) {
        execute(out, () -> administration.forceCancel(tenant(), required(request.getSagaId(), "saga_id"), actor(), required(request.getReason(), "reason")));
    }
    @Override public void forceCompleteOrder(OrderSagaActionRequest request, StreamObserver<OrderSagaResponse> out) {
        execute(out, () -> administration.forceComplete(tenant(), required(request.getSagaId(), "saga_id"), actor(), required(request.getReason(), "reason")));
    }
    @Override public void markOrderSagaResolved(OrderSagaActionRequest request, StreamObserver<OrderSagaResponse> out) {
        execute(out, () -> administration.markManuallyResolved(tenant(), required(request.getSagaId(), "saga_id"), actor(), required(request.getReason(), "reason")));
    }

    private String tenant() { return GrpcSecurityContext.requireTenant(); }
    private String actor() { return GrpcSecurityContext.requireSubject(); }
    private void execute(StreamObserver<OrderSagaResponse> out, Action action) {
        try {
            out.onNext(OrderSagaResponse.newBuilder().setSaga(map(action.run())).build());
            out.onCompleted();
        } catch (IllegalArgumentException e) {
            Status status = e.getMessage() != null && (e.getMessage().equals("saga not found") || e.getMessage().equals("saga step not found"))
                    ? Status.NOT_FOUND : Status.INVALID_ARGUMENT;
            out.onError(status.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalStateException e) {
            out.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (io.grpc.StatusRuntimeException e) { out.onError(e); }
        catch (RuntimeException e) { out.onError(Status.INTERNAL.withDescription("order saga administration failed").asRuntimeException()); }
    }
    private OrderSaga map(OrderSagaInspection value) {
        OrderSaga.Builder result = OrderSaga.newBuilder().setSagaId(value.sagaId()).setOrderId(value.orderId())
                .setStatus(value.status()).setAttemptCount(value.attemptCount());
        put(value.terminalIntent(), result::setTerminalIntent); put(value.currentStepKey(), result::setCurrentStepKey);
        put(value.lastErrorCode(), result::setLastErrorCode); put(value.lastErrorMessage(), result::setLastErrorMessage);
        if (value.nextAttemptAt()!=null) result.setNextAttemptAt(ts(value.nextAttemptAt()));
        if (value.claimedAt()!=null) result.setClaimedAt(ts(value.claimedAt()));
        if (value.compensationDeadlineAt()!=null) result.setCompensationDeadlineAt(ts(value.compensationDeadlineAt()));
        value.steps().stream().map(this::map).forEach(result::addSteps);
        return result.build();
    }
    private OrderSagaStep map(OrderSagaInspection.Step value) {
        OrderSagaStep.Builder result=OrderSagaStep.newBuilder().setStepKey(value.stepKey()).setSequenceNo(value.sequenceNo())
                .setStepType(value.stepType()).setStatus(value.status()).setAttemptCount(value.attemptCount());
        put(value.orderLineId(), result::setOrderLineId); put(value.externalReferenceId(), result::setExternalReferenceId);
        put(value.lastErrorCode(), result::setLastErrorCode); put(value.lastErrorMessage(), result::setLastErrorMessage);
        if(value.nextAttemptAt()!=null) result.setNextAttemptAt(ts(value.nextAttemptAt()));
        if(value.claimedAt()!=null) result.setClaimedAt(ts(value.claimedAt()));
        return result.build();
    }
    private Timestamp ts(Instant value) { return Timestamp.newBuilder().setSeconds(value.getEpochSecond()).setNanos(value.getNano()).build(); }
    private void put(String value, java.util.function.Consumer<String> setter) { if(value!=null && !value.isBlank()) setter.accept(value); }
    private String required(String value,String field){ if(value==null||value.isBlank()) throw new IllegalArgumentException(field+" required"); return value.trim(); }
    @FunctionalInterface private interface Action { OrderSagaInspection run(); }
}
