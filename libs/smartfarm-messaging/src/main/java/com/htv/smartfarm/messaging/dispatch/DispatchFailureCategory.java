package com.htv.smartfarm.messaging.dispatch;

/**
 * Generic classification of a dispatch/relay failure, independent of any service's business types.
 *
 * <ul>
 *   <li>{@link #PERMANENT_DATA} - the message/payload/topic can never succeed by retrying
 *       (invalid topic segment, malformed JSON, unsupported command). Dead-letter + continue.</li>
 *   <li>{@link #TRANSIENT_INFRASTRUCTURE} - the broker/transport is down or timing out. Retry with
 *       backoff and BREAK the batch to avoid hammering a dead broker.</li>
 *   <li>{@link #TRANSIENT_APPLICATION} - a retryable application/DB failure (optimistic lock,
 *       transient downstream). Retry with limit; continue when the item is independent.</li>
 *   <li>{@link #UNKNOWN} - unclassified. Retry with limit + metric; never dead-letter on first sight,
 *       never spin infinitely.</li>
 * </ul>
 */
public enum DispatchFailureCategory {
    PERMANENT_DATA,
    TRANSIENT_INFRASTRUCTURE,
    TRANSIENT_APPLICATION,
    UNKNOWN
}
