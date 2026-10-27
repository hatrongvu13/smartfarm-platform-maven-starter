package com.htv.farmverse.health.outbox;

/**
 * Broker abstraction, so the relay can be tested without a broker.
 */
public interface HealthEventPublisher {
    void publish(String topic, byte[] payload) throws Exception;
}
