package com.htv.smartfarm.messaging.dispatch;

/**
 * The action a relay/dispatcher should take for one failed item. Exactly one of
 * {@code markDead} / {@code retry} is the terminal-vs-retry intent, and exactly one of
 * {@code continueBatch} / {@code breakBatch} is the loop control. A classifier returns this;
 * the caller maps it onto its own repository API (markDead / markFailed-with-next-attempt) and
 * loop (continue / break). No business type leaks in.
 */
public record DispatchFailureDecision(
        DispatchFailureCategory category,
        boolean markDead,
        boolean retry,
        boolean continueBatch,
        boolean breakBatch
) {
    public DispatchFailureDecision {
        if (markDead == retry) {
            throw new IllegalArgumentException("exactly one of markDead/retry must be true");
        }
        if (continueBatch == breakBatch) {
            throw new IllegalArgumentException("exactly one of continueBatch/breakBatch must be true");
        }
    }

    /** Permanent data error: dead-letter the row and keep processing the rest of the batch. */
    public static DispatchFailureDecision deadAndContinue(DispatchFailureCategory category) {
        return new DispatchFailureDecision(category, true, false, true, false);
    }

    /** Infrastructure error: schedule a retry and stop the batch (don't hammer a dead broker). */
    public static DispatchFailureDecision retryAndBreak(DispatchFailureCategory category) {
        return new DispatchFailureDecision(category, false, true, false, true);
    }

    /** Independent transient error: schedule a retry but keep processing other items. */
    public static DispatchFailureDecision retryAndContinue(DispatchFailureCategory category) {
        return new DispatchFailureDecision(category, false, true, true, false);
    }
}
