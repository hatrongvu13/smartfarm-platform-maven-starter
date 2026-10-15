package com.htv.smartfarm.identity.authorization.domain;

import java.util.Locale;
import java.util.regex.Pattern;

import com.htv.smartfarm.common.contract.PermissionCodeContract;

/** Canonical permission identifier owned by Identity. */
public final class PermissionCode {
    private static final Pattern PATTERN = Pattern.compile(
            "[a-z][a-z0-9-]{0,39}(?::[a-z][a-z0-9-]{0,39}){1,5}"
    );

    private PermissionCode() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("permission code must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (PermissionCodeContract.WILDCARD.equals(normalized)) return normalized;
        if (!PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid permission code: " + value);
        }
        return normalized;
    }

    public static String resourceType(String code) {
        String normalized = normalize(code);
        if (PermissionCodeContract.WILDCARD.equals(normalized)) {
            return PermissionCodeContract.WILDCARD;
        }
        int separator = normalized.lastIndexOf(':');
        return normalized.substring(0, separator).replace(':', '-');
    }

    public static String action(String code) {
        String normalized = normalize(code);
        if (PermissionCodeContract.WILDCARD.equals(normalized)) {
            return PermissionCodeContract.WILDCARD;
        }
        return normalized.substring(normalized.lastIndexOf(':') + 1);
    }
}
