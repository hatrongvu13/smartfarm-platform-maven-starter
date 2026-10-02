package com.htv.smartfarm.messaging.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;

class DispatchClassifierTest {

    private final DefaultDispatchFailureClassifier classifier = new DefaultDispatchFailureClassifier();

    @Test
    void mqttExceptionIsInfrastructureRetryBreak() {
        var d = classifier.classify(new RuntimeException(new MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT)));
        assertThat(d.category()).isEqualTo(DispatchFailureCategory.TRANSIENT_INFRASTRUCTURE);
        assertThat(d.retry()).isTrue();
        assertThat(d.breakBatch()).isTrue();
        assertThat(d.markDead()).isFalse();
    }

    @Test
    void illegalArgumentIsPermanentDataDeadContinue() {
        var d = classifier.classify(new IllegalArgumentException("Invalid MQTT topic segment"));
        assertThat(d.category()).isEqualTo(DispatchFailureCategory.PERMANENT_DATA);
        assertThat(d.markDead()).isTrue();
        assertThat(d.continueBatch()).isTrue();
    }

    @Test
    void unknownIsRetryContinueNotDead() {
        var d = classifier.classify(new IllegalStateException("odd"));
        assertThat(d.category()).isEqualTo(DispatchFailureCategory.UNKNOWN);
        assertThat(d.markDead()).isFalse();
        assertThat(d.retry()).isTrue();
        assertThat(d.continueBatch()).isTrue();
    }

    @Test
    void serviceSpecificOverrideWinsOverPermanentAndUnknown() {
        var custom = new DefaultDispatchFailureClassifier() {
            @Override
            protected DispatchFailureDecision classifyServiceSpecific(Throwable error) {
                if (error instanceof IllegalStateException) {
                    return DispatchFailureDecision.retryAndContinue(DispatchFailureCategory.TRANSIENT_APPLICATION);
                }
                return null;
            }
        };
        var d = custom.classify(new IllegalStateException("optimistic lock"));
        assertThat(d.category()).isEqualTo(DispatchFailureCategory.TRANSIENT_APPLICATION);
        // infrastructure still wins even for a service that overrides
        var infra = custom.classify(new MqttException(MqttException.REASON_CODE_BROKER_UNAVAILABLE));
        assertThat(infra.category()).isEqualTo(DispatchFailureCategory.TRANSIENT_INFRASTRUCTURE);
    }

    @Test
    void decisionRejectsContradictoryFlags() {
        assertThatThrownBy(() -> new DispatchFailureDecision(
                DispatchFailureCategory.UNKNOWN, true, true, true, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DispatchFailureDecision(
                DispatchFailureCategory.UNKNOWN, true, false, true, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void backoffIsCappedAndMonotonic() {
        var backoff = new ExponentialBackoff(Duration.ofSeconds(2), Duration.ofSeconds(30));
        assertThat(backoff.delayFor(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(backoff.delayFor(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(backoff.delayFor(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(backoff.delayFor(10)).isEqualTo(Duration.ofSeconds(30)); // capped
    }

    @Test
    void errorCodeIsSanitizedAndTruncated() {
        assertThat(DispatchErrorCodes.codeOf(new IllegalArgumentException("x"), 120))
                .isEqualTo("IllegalArgumentException");
        assertThat(DispatchErrorCodes.sanitize("line1\nline2\tsecret", 10)).hasSizeLessThanOrEqualTo(10);
        assertThat(DispatchErrorCodes.sanitize("  ", 10)).isEqualTo("UNKNOWN");
    }
}
