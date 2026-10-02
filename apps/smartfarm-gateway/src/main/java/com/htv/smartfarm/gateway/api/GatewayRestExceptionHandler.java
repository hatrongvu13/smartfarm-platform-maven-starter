package com.htv.smartfarm.gateway.api;

import com.htv.smartfarm.gateway.context.GatewayCorrelationContext;
import com.htv.smartfarm.gateway.grpc.GatewayRestException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

@RestControllerAdvice
@Order(-2)
public class GatewayRestExceptionHandler {
    @ExceptionHandler(GatewayRestException.class)
    public ResponseEntity<Map<String,Object>> grpc(GatewayRestException exception, ServerWebExchange exchange) {
        return response(exception.status(), exception.code(), exception.getMessage(), exchange);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,Object>> validation(IllegalArgumentException exception, ServerWebExchange exchange) {
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message(exception, "Invalid request"), exchange);
    }

    private ResponseEntity<Map<String,Object>> response(HttpStatus status, String code, String message,
            ServerWebExchange exchange) {
        String correlation = GatewayCorrelationContext.normalize(
                exchange.getRequest().getHeaders().getFirst("X-Correlation-Id"));
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        body.put("path", exchange.getRequest().getPath().value());
        body.put("correlationId", correlation);
        return ResponseEntity.status(status).body(body);
    }

    private String message(RuntimeException exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? fallback : exception.getMessage();
    }
}
