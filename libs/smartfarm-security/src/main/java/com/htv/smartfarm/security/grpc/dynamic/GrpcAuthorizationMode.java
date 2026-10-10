package com.htv.smartfarm.security.grpc.dynamic;

import java.util.Locale;

public enum GrpcAuthorizationMode {
    OFF,
    SHADOW,
    ENFORCE;

    public static GrpcAuthorizationMode parse(String value) {
        if (value == null || value.isBlank()) return OFF;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "smartfarm.security.grpc.authorization-mode must be OFF, SHADOW or ENFORCE",
                    exception
            );
        }
    }
}
