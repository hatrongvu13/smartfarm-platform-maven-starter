package com.htv.smartfarm.order.saga.administration;

public enum OrderSagaRecoveryAction {
    RETRY_STEP,
    RESUME_SAGA,
    RECOVER_STALE,
    FORCE_COMPENSATE,
    FORCE_CANCEL,
    FORCE_COMPLETE,
    MARK_MANUALLY_RESOLVED
}
