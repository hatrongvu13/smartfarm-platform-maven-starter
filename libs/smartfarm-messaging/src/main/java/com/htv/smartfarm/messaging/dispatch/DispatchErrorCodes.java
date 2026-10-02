package com.htv.smartfarm.messaging.dispatch;

/**
 * Produces a short, safe error code to persist as {@code last_error_code}. Strips control characters
 * and truncates, so a verbose/secret-bearing exception message never lands in the DB or logs. Prefer
 * a stable short code (exception simple name) over the full message.
 */
public final class DispatchErrorCodes {

    private DispatchErrorCodes() {
    }

    /** Short stable code: the exception's simple class name, truncated/sanitized. */
    public static String codeOf(Throwable error, int maxLength) {
        if (error == null) return "UNKNOWN";
        return sanitize(error.getClass().getSimpleName(), maxLength);
    }

    /** Sanitize + truncate an arbitrary string for safe persistence. Never returns null. */
    public static String sanitize(String value, int maxLength) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        String cleaned = value.strip().replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ");
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, Math.max(0, maxLength));
        }
        return cleaned.isBlank() ? "UNKNOWN" : cleaned;
    }
}
