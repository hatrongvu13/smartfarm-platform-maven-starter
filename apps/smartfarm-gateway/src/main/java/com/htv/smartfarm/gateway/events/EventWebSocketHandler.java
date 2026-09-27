package com.htv.smartfarm.gateway.events;

import java.net.URI;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Streams SmartFarm domain events to the browser over WebSocket at {@code /ws/events}.
 *
 * <p>Auth: a WebSocket handshake cannot easily carry an Authorization header from the browser,
 * so the client passes the access token as a query parameter {@code ?token=<jwt>}. The token is
 * verified with the same reactive JWT decoder as the REST chain; on failure the socket is closed.
 * Events are then filtered to the token's {@code tenant_id} (defence in depth — a client only
 * sees its own tenant), and optionally to a single farm via {@code ?farmId=<id>}.
 */
@Component
public class EventWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EventWebSocketHandler.class);
    private static final Pattern TOKEN = Pattern.compile("(?:^|&)token=([^&]+)");
    private static final Pattern FARM = Pattern.compile("(?:^|&)farmId=([^&]+)");

    private final DomainEventBus bus;
    private final ReactiveJwtDecoder decoder;

    public EventWebSocketHandler(DomainEventBus bus, ReactiveJwtDecoder decoder) {
        this.bus = bus;
        this.decoder = decoder;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        String token = group(TOKEN, query);
        String farmFilter = decode(group(FARM, query));
        if (token == null || token.isBlank()) {
            return session.close();
        }
        return decoder.decode(decode(token))
                .flatMap(jwt -> {
                    String tenant = jwt.getClaimAsString("tenant_id");
                    if (tenant == null || tenant.isBlank()) return session.close();
                    Flux<String> events = bus.stream()
                            .filter(e -> tenant.equals(e.tenantId()))
                            .filter(e -> farmFilter == null || farmFilter.isBlank() || farmFilter.equals(e.farmId()))
                            .map(DomainEventBus.Envelope::json);
                    // A tiny hello frame confirms the socket authenticated, then live events flow.
                    Flux<String> out = Flux.concat(
                            Mono.just("{\"type\":\"connected\",\"tenantId\":\"" + tenant + "\"}"),
                            events);
                    return session.send(out.map(session::textMessage));
                })
                .onErrorResume(err -> {
                    log.debug("WS handshake rejected: {}", err.getClass().getSimpleName());
                    return session.close();
                });
    }

    private static String group(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String decode(String s) {
        if (s == null) return null;
        return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
