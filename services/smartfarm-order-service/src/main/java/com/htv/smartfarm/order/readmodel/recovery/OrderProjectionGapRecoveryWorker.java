package com.htv.smartfarm.order.readmodel.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "smartfarm.order.readmodel.gap-recovery",
        name = "enabled", havingValue = "true")
public class OrderProjectionGapRecoveryWorker {
    private static final Logger log = LoggerFactory.getLogger(OrderProjectionGapRecoveryWorker.class);
    private final OrderProjectionGapRecoveryTransactionService transactions;
    private final OrderProjectionGapRecoveryExecutor executor;
    public OrderProjectionGapRecoveryWorker(OrderProjectionGapRecoveryTransactionService transactions,
            OrderProjectionGapRecoveryExecutor executor) {
        this.transactions = transactions; this.executor = executor;
    }
    @Scheduled(fixedDelayString = "${smartfarm.order.readmodel.gap-recovery.poll-interval:5s}")
    public void run() {
        transactions.recoverStaleClaims();
        for (OrderProjectionGapClaim claim : transactions.claimBatch()) process(claim);
    }
    private void process(OrderProjectionGapClaim claim) {
        try {
            var result = executor.recover(claim);
            if (!result.archiveAvailable()) {
                transactions.retry(claim, "ARCHIVE_EVENT_NOT_FOUND",
                        "Expected aggregate version is not available in the event archive");
            } else if (result.resolved()) {
                transactions.resolved(claim, result.projectionVersion());
            } else {
                transactions.progressed(claim, result.projectionVersion());
            }
        } catch (RuntimeException exception) {
            transactions.retry(claim, exception.getClass().getSimpleName(), safeMessage(exception));
            log.warn("Projection gap recovery deferred: gapId={} reason={}",
                    claim.gapId(), exception.getClass().getSimpleName());
        }
    }
    private String safeMessage(RuntimeException exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank() ? exception.getClass().getSimpleName() : value;
    }
}
