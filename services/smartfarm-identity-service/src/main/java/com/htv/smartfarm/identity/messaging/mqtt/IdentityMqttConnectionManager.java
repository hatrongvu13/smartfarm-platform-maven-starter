package com.htv.smartfarm.identity.messaging.mqtt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import com.htv.smartfarm.identity.messaging.command.IdentityMqttCommandIntake;
import com.htv.smartfarm.identity.messaging.command.IdentityMqttCommandProperties;
import com.htv.smartfarm.security.mqtt.MqttSecurityVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "smartfarm.identity.mqtt",
        name = "enabled",
        havingValue = "true"
)
public class IdentityMqttConnectionManager implements MqttCallbackExtended {

    public enum State { DISCONNECTED, CONNECTING, CONNECTED, DEGRADED, STOPPED }

    public record Snapshot(
            State state,
            boolean connected,
            int consecutiveFailures,
            Instant startedAt,
            Instant lastConnectedAt,
            Instant lastDisconnectedAt,
            String lastErrorCode,
            String clientId
    ) { }

    private static final Logger log = LoggerFactory.getLogger(IdentityMqttConnectionManager.class);

    private final IdentityMqttProperties properties;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<State> state = new AtomicReference<>(State.DISCONNECTED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicBoolean connectInProgress = new AtomicBoolean();
    private final Instant startedAt;
    private ObjectProvider<IdentityMqttCommandIntake> commandIntake;
    private ObjectProvider<IdentityMqttCommandProperties> commandProperties;
    private volatile Instant lastConnectedAt;
    private volatile Instant lastDisconnectedAt;
    private volatile String lastErrorCode;
    private volatile ScheduledFuture<?> scheduledConnect;
    private volatile MqttAsyncClient client;
    private volatile MqttConnectOptions options;

    @Autowired
    public IdentityMqttConnectionManager(IdentityMqttProperties properties) {
        this(properties, Clock.systemUTC(), Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "identity-mqtt-lifecycle");
            thread.setDaemon(true);
            return thread;
        }));
    }

    @Autowired
    void setCommandSupport(
            ObjectProvider<IdentityMqttCommandIntake> commandIntake,
            ObjectProvider<IdentityMqttCommandProperties> commandProperties
    ) {
        this.commandIntake = commandIntake;
        this.commandProperties = commandProperties;
    }

    // App-level MQTT message signing. Default is the disabled verifier so the test
    // constructor and any context without the bean keep the original behaviour.
    private com.htv.smartfarm.security.mqtt.MqttSecurityVerifier mqttSecurity =
            new com.htv.smartfarm.security.mqtt.MqttSecurityVerifier(
                    com.htv.smartfarm.security.mqtt.MqttSecurityProperties.disabled());

    @Autowired(required = false)
    void setMqttSecurity(com.htv.smartfarm.security.mqtt.MqttSecurityVerifier mqttSecurity) {
        if (mqttSecurity != null) {
            this.mqttSecurity = mqttSecurity;
        }
    }

    IdentityMqttConnectionManager(
            IdentityMqttProperties properties,
            Clock clock,
            ScheduledExecutorService scheduler
    ) {
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.startedAt = clock.instant();
    }

    @PostConstruct
    public void start() {
        try {
            client = new MqttAsyncClient(
                    properties.brokerUri(),
                    properties.clientId(),
                    new MemoryPersistence()
            );
            client.setCallback(this);
            options = connectOptions();
            scheduleConnect(Duration.ZERO);
        } catch (MqttException exception) {
            recordFailure(exception);
            scheduleConnect(properties.initialRetry());
        }
    }

    public void publish(String topic, byte[] payload, int qos) throws MqttException {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("MQTT topic must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("MQTT payload must not be null");
        }
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("MQTT qos must be 0..2");
        }
        MqttAsyncClient current = client;
        if (current == null || !current.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        MqttMessage message = new MqttMessage(mqttSecurity.sign(topic, payload));
        message.setQos(qos);
        message.setRetained(false);
        current.publish(topic, message).waitForCompletion(
                properties.connectionTimeout().toMillis()
        );
    }

    public Snapshot snapshot() {
        MqttAsyncClient current = client;
        return new Snapshot(
                state.get(),
                current != null && current.isConnected(),
                consecutiveFailures.get(),
                startedAt,
                lastConnectedAt,
                lastDisconnectedAt,
                lastErrorCode,
                properties.clientId()
        );
    }

    private MqttConnectOptions connectOptions() {
        MqttConnectOptions value = new MqttConnectOptions();
        value.setAutomaticReconnect(true);
        value.setCleanSession(properties.cleanSession());
        value.setConnectionTimeout(seconds(properties.connectionTimeout()));
        value.setKeepAliveInterval(seconds(properties.keepAlive()));
        if (properties.username() != null) {
            value.setUserName(properties.username());
            value.setPassword(properties.password() == null
                    ? new char[0]
                    : properties.password().toCharArray());
        }
        return value;
    }

    private void scheduleConnect(Duration delay) {
        if (state.get() == State.STOPPED || scheduler.isShutdown()) return;
        ScheduledFuture<?> previous = scheduledConnect;
        if (previous != null && !previous.isDone()) return;
        scheduledConnect = scheduler.schedule(
                this::connectNow,
                Math.max(0, delay.toMillis()),
                TimeUnit.MILLISECONDS
        );
    }

    private void connectNow() {
        MqttAsyncClient current = client;
        if (state.get() == State.STOPPED || current == null || current.isConnected()) return;
        if (!connectInProgress.compareAndSet(false, true)) return;
        state.set(State.CONNECTING);
        try {
            current.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    connectInProgress.set(false);
                    markConnected(false, current.getServerURI());
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    connectInProgress.set(false);
                    recordFailure(exception);
                    scheduleConnect(nextDelay());
                }
            });
        } catch (MqttException exception) {
            connectInProgress.set(false);
            recordFailure(exception);
            scheduleConnect(nextDelay());
        }
    }

    private Duration nextDelay() {
        int failures = consecutiveFailures.get();
        Duration base;
        if (Duration.between(startedAt, clock.instant()).compareTo(properties.probeAfter()) >= 0) {
            base = properties.probeInterval();
        } else if (failures > properties.degradedAfterAttempts()) {
            base = properties.degradedRetry();
        } else {
            long initial = properties.initialRetry().toMillis();
            long maximum = properties.maximumFastRetry().toMillis();
            long exponential = initial * (1L << Math.min(10, Math.max(0, failures - 1)));
            base = Duration.ofMillis(Math.min(maximum, exponential));
        }
        long jitterBound = Math.max(1, base.toMillis() / 5);
        long jitter = ThreadLocalRandom.current().nextLong(-jitterBound, jitterBound + 1);
        return Duration.ofMillis(Math.max(1000, base.toMillis() + jitter));
    }

    private void recordFailure(Throwable throwable) {
        int failures = consecutiveFailures.incrementAndGet();
        lastDisconnectedAt = clock.instant();
        lastErrorCode = throwable == null ? "UNKNOWN" : throwable.getClass().getSimpleName();
        state.set(failures > properties.degradedAfterAttempts()
                ? State.DEGRADED
                : State.DISCONNECTED);
        log.warn(
                "Identity MQTT unavailable: state={}, attempt={}, error={}",
                state.get(), failures, lastErrorCode
        );
    }

    private void markConnected(boolean reconnect, String serverUri) {
        consecutiveFailures.set(0);
        lastErrorCode = null;
        lastConnectedAt = clock.instant();
        state.set(State.CONNECTED);
        subscribeCommands();
        log.info(
                "Identity MQTT connected: clientId={}, reconnect={}, broker={}",
                properties.clientId(), reconnect, sanitize(serverUri)
        );
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        connectInProgress.set(false);
        markConnected(reconnect, serverURI);
    }

    @Override
    public void connectionLost(Throwable cause) {
        connectInProgress.set(false);
        recordFailure(cause);
        scheduleConnect(properties.degradedRetry());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        IdentityMqttCommandIntake intake = commandIntake == null
                ? null
                : commandIntake.getIfAvailable();
        IdentityMqttCommandProperties commandConfig = commandProperties == null
                ? null
                : commandProperties.getIfAvailable();
        if (intake == null || commandConfig == null || !commandConfig.enabled()) return;
        MqttSecurityVerifier.Result verification =
                mqttSecurity.verify(topic, message.getPayload());
        if (!verification.accepted()) {
            log.warn(
                    "Identity MQTT command rejected by message security: topic={}, reason={}",
                    topic,
                    verification.reason()
            );
            return;
        }
        try {
            intake.accept(topic, verification.payload());
        } catch (RuntimeException exception) {
            log.error(
                    "Identity MQTT command intake failed: topic={}, error={}",
                    topic,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void subscribeCommands() {
        IdentityMqttCommandProperties commandConfig = commandProperties == null
                ? null
                : commandProperties.getIfAvailable();
        MqttAsyncClient current = client;
        if (commandConfig == null || !commandConfig.enabled()
                || current == null || !current.isConnected()) return;
        try {
            current.subscribe(
                    commandConfig.topicFilter(),
                    commandConfig.qos()
            ).waitForCompletion(properties.connectionTimeout().toMillis());
            log.info(
                    "Identity MQTT command subscription active: filter={}, qos={}",
                    commandConfig.topicFilter(),
                    commandConfig.qos()
            );
        } catch (MqttException exception) {
            log.warn(
                    "Identity MQTT command subscription deferred: error={}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    @Override
    public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
    }

    @PreDestroy
    public void destroy() {
        stop();
    }

    public void stop() {
        if (state.getAndSet(State.STOPPED) == State.STOPPED) return;
        ScheduledFuture<?> pending = scheduledConnect;
        if (pending != null) pending.cancel(false);
        scheduler.shutdownNow();
        MqttAsyncClient current = client;
        if (current != null) {
            try {
                if (current.isConnected()) current.disconnectForcibly(1000, 1000);
                current.close();
            } catch (MqttException exception) {
                log.debug("Identity MQTT shutdown completed with {}", exception.getClass().getSimpleName());
            }
        }
    }

    private static int seconds(Duration duration) {
        return (int) Math.max(1, duration.toSeconds());
    }

    private static String sanitize(String uri) {
        if (uri == null) return "unknown";
        int scheme = uri.indexOf("://");
        int at = uri.indexOf('@');
        return scheme >= 0 && at > scheme ? uri.substring(0, scheme + 3) + uri.substring(at + 1) : uri;
    }
}
