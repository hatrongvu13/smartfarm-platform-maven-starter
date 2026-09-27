package com.htv.smartfarm.gateway.events;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;

/**
 * Maps the WebSocket event stream at {@code /ws/events}. Ordered ahead of the annotated-controller
 * mapping so the upgrade request is handled here. Authentication is enforced inside
 * {@link EventWebSocketHandler} via the {@code ?token=} query parameter.
 */
@Configuration(proxyBeanMethods = false)
public class EventWebSocketConfig {

    @Bean
    HandlerMapping eventWebSocketMapping(EventWebSocketHandler handler) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/ws/events", (WebSocketHandler) handler));
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return mapping;
    }
}
