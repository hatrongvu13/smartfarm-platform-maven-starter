package com.htv.smartfarm.messaging.dispatch;

import org.eclipse.paho.client.mqttv3.MqttException;

/**
 * Baseline classifier shared by every relay/dispatcher:
 *
 * <ul>
 *   <li>Any {@link MqttException} (or a cause chain containing one) =&gt; TRANSIENT_INFRASTRUCTURE,
 *       retry + break (the broker is unreachable; stop the batch).</li>
 *   <li>{@link IllegalArgumentException} =&gt; PERMANENT_DATA, dead + continue (bad topic/payload/command
 *       cannot be fixed by retry).</li>
 *   <li>Anything else =&gt; UNKNOWN, retry + continue (bounded by the caller's max-attempts; never
 *       dead-letter on first sight, never break the whole batch for one odd item).</li>
 * </ul>
 *
 * A service with extra retryable application exceptions (e.g. optimistic-lock) overrides
 * {@link #classifyServiceSpecific(Throwable)} to return TRANSIENT_APPLICATION decisions; it must NOT
 * reference Paho types itself - that mapping stays here, in infrastructure.
 */
public class DefaultDispatchFailureClassifier implements DispatchFailureClassifier {

    @Override
    public final DispatchFailureDecision classify(Throwable error) {
        if (containsMqttException(error)) {
            return DispatchFailureDecision.retryAndBreak(DispatchFailureCategory.TRANSIENT_INFRASTRUCTURE);
        }
        DispatchFailureDecision serviceSpecific = classifyServiceSpecific(error);
        if (serviceSpecific != null) {
            return serviceSpecific;
        }
        if (isPermanentData(error)) {
            return DispatchFailureDecision.deadAndContinue(DispatchFailureCategory.PERMANENT_DATA);
        }
        return DispatchFailureDecision.retryAndContinue(DispatchFailureCategory.UNKNOWN);
    }

    /**
     * Hook for service-specific retryable application errors. Return {@code null} to fall through to
     * the default permanent-data / unknown handling. Never match infrastructure here.
     */
    protected DispatchFailureDecision classifyServiceSpecific(Throwable error) {
        return null;
    }

    protected boolean isPermanentData(Throwable error) {
        return rootOrSelf(error) instanceof IllegalArgumentException
                || hasCause(error, IllegalArgumentException.class);
    }

    protected static boolean containsMqttException(Throwable error) {
        return hasCause(error, MqttException.class);
    }

    protected static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }

    protected static Throwable rootOrSelf(Throwable error) {
        Throwable t = error;
        while (t != null && t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t == null ? error : t;
    }
}
