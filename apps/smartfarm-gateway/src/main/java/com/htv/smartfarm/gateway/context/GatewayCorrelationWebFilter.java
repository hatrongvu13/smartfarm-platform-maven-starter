package com.htv.smartfarm.gateway.context;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayCorrelationWebFilter implements WebFilter {
    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = GatewayCorrelationContext.normalize(exchange.getRequest().getHeaders().getFirst(HEADER));
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        ServerWebExchange correlated = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER, correlationId)))
                .build();
        return chain.filter(correlated).contextWrite(context -> context.put(GatewayCorrelationContext.KEY, correlationId));
    }
}
