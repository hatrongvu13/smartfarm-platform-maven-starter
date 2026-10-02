package com.htv.smartfarm.order.saga.worker;

import com.htv.smartfarm.order.saga.persistence.OrderSagaClaim;
import com.htv.smartfarm.order.saga.persistence.OrderSagaProperties;
import com.htv.smartfarm.order.saga.persistence.OrderSagaStepStatus;
import com.htv.smartfarm.order.saga.persistence.OrderSagaTransactionService;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "smartfarm.order.saga", name = "enabled", havingValue = "true")
public class OrderSagaPersistentWorker {
    private static final Logger log = LoggerFactory.getLogger(OrderSagaPersistentWorker.class);
    private final OrderSagaTransactionService transactions;
    private final OrderSagaForwardStepExecutor executor;
    private final OrderSagaCheckpointService checkpoints;
    private final OrderSagaCompensationStepExecutor compensationExecutor;

    public OrderSagaPersistentWorker(OrderSagaTransactionService transactions,
            OrderSagaForwardStepExecutor executor, OrderSagaCheckpointService checkpoints,
            OrderSagaProperties properties, OrderSagaCompensationStepExecutor compensationExecutor) {
        this.transactions = transactions; this.executor = executor;
        this.checkpoints = checkpoints;
        this.compensationExecutor = compensationExecutor;
    }

    @Scheduled(fixedDelayString = "${smartfarm.order.saga.poll-interval:1s}")
    public void run() {
        transactions.recoverStaleClaims();
        transactions.expireManualReviewsAndProcessingDeadlines();
        for (OrderSagaClaim claim : transactions.claimBatch()) {
            processOne(claim);
        }
    }

    private void processOne(OrderSagaClaim claim) {
        if (claim.status() == com.htv.smartfarm.order.saga.persistence.OrderSagaStatus.COMPENSATING) {
            processCompensation(claim);
            return;
        }
        String stepKey = transactions.nextForwardStepKey(claim.sagaId());
        if (stepKey == null) {
            if (transactions.forwardStepsCompleted(claim.sagaId())) {
                checkpoints.completed(claim);
            }
            return;
        }
        var step = transactions.claimStep(claim.sagaId(), stepKey);
        if (step.getStatus() == OrderSagaStepStatus.MANUAL_REVIEW) return;
        try {
            checkpoints.started(claim, step);
            OrderSagaStepResult result = executor.execute(claim, step);
            checkpoints.succeeded(claim, step, result);
        } catch (StatusRuntimeException exception) {
            String code = exception.getStatus().getCode().name();
            String message = description(exception);
            transactions.markForwardStepFailed(claim.sagaId(), stepKey, code, message);
            checkpoints.forwardFailed(claim, step, code, message);
        } catch (RuntimeException exception) {
            String code = exception.getClass().getSimpleName();
            String message = safeMessage(exception);
            transactions.markForwardStepFailed(claim.sagaId(), stepKey, code, message);
            checkpoints.forwardFailed(claim, step, code, message);
        }
    }

    private void processCompensation(OrderSagaClaim claim) {
        checkpoints.compensationStarted(claim);
        String stepKey = transactions.nextCompensationStepKey(claim.sagaId());
        if (stepKey == null) {
            if (transactions.compensationStepsCompleted(claim.sagaId())) {
                checkpoints.compensationCompleted(claim);
            }
            return;
        }
        var step = transactions.claimStep(claim.sagaId(), stepKey);
        if (step.getStatus() == OrderSagaStepStatus.MANUAL_REVIEW) {
            checkpoints.compensationManualReview(claim, "Compensation retry attempts exhausted");
            return;
        }
        try {
            compensationExecutor.execute(claim, step);
            checkpoints.compensatedStep(claim, step);
        } catch (StatusRuntimeException exception) {
            var action = transactions.markCompensationStepFailed(
                    claim.sagaId(), stepKey,
                    exception.getStatus().getCode().name(), description(exception));
            checkpoints.compensationFailed(claim, step,
                    exception.getStatus().getCode().name(), description(exception));
            if (action == com.htv.smartfarm.order.saga.persistence.OrderSagaFailureAction.MANUAL_REVIEW) {
                checkpoints.compensationManualReview(claim, description(exception));
            }
        } catch (RuntimeException exception) {
            var action = transactions.markCompensationStepFailed(
                    claim.sagaId(), stepKey,
                    exception.getClass().getSimpleName(), safeMessage(exception));
            checkpoints.compensationFailed(claim, step,
                    exception.getClass().getSimpleName(), safeMessage(exception));
            if (action == com.htv.smartfarm.order.saga.persistence.OrderSagaFailureAction.MANUAL_REVIEW) {
                checkpoints.compensationManualReview(claim, safeMessage(exception));
            }
        }
    }

    private String description(StatusRuntimeException exception) {
        String value = exception.getStatus().getDescription();
        return value == null || value.isBlank() ? exception.getStatus().getCode().name() : value;
    }

    private String safeMessage(RuntimeException exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank() ? exception.getClass().getSimpleName() : value;
    }
}
