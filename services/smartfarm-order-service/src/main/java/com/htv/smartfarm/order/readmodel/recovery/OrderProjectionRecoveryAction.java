package com.htv.smartfarm.order.readmodel.recovery;

public enum OrderProjectionRecoveryAction {
    AUTO_REPLAY,
    MANUAL_RETRY,
    MANUAL_REPLAY,
    GAP_RESOLVED,
    GAP_MANUAL_REVIEW,
    STALE_CLAIM_RECOVERED
}
