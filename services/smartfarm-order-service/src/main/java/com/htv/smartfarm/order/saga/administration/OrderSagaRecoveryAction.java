package com.htv.smartfarm.order.saga.administration;

public enum OrderSagaRecoveryAction {
    RETRY_STEP,
    RESUME_SAGA,
    RECOVER_STALE
}
