package com.htv.smartfarm.gateway.events;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import org.springframework.stereotype.Component;

/**
 * In-process fan-out bus between the MQTT subscriber and the WebSocket handler. The subscriber
 * publishes one {@link Envelope} per domain event; each WebSocket subscriber gets a shared,
 * multicast view and filters it by its own tenant (and optional farm). A bounded replay of the
 * latest events lets a just-connected client see very recent activity.
 */
@Component
public class DomainEventBus {

    /**
     * One event as delivered to WebSocket clients.
     */
    public record Envelope(String tenantId, String farmId, String topic, String json) {
    }

    private final Sinks.Many<Envelope> sink = Sinks.many().multicast().onBackpressureBuffer(256, false);

    public void publish(Envelope e) {
        sink.emitNext(e, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    public Flux<Envelope> stream() {
        return sink.asFlux();
    }
}
