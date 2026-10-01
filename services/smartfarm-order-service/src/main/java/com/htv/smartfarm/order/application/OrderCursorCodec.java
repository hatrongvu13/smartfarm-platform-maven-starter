package com.htv.smartfarm.order.application;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class OrderCursorCodec {

    public record Cursor(long createdAt, String orderId) { }

    public String encode(long createdAt, String orderId) {
        String raw = createdAt + ":" + required(orderId);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int separator = raw.indexOf(':');
            if (separator <= 0 || separator == raw.length() - 1) throw new IllegalArgumentException();
            return new Cursor(Long.parseLong(raw.substring(0, separator)), required(raw.substring(separator + 1)));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("pageToken is invalid");
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException("cursor orderId is invalid");
        }
        return value.trim();
    }
}
