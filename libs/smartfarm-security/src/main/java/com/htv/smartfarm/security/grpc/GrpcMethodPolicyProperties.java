package com.htv.smartfarm.security.grpc;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.htv.smartfarm.security.jwt.JwtAuthorities;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.security.grpc")
public record GrpcMethodPolicyProperties(
        Map<String, String> authorities,
        Set<String> authenticatedMethods,
        Set<String> publicMethods,
        boolean requireExplicitPolicy
) {

    private static final Set<String> DEFAULT_PUBLIC_METHODS = Set.of(
            "grpc.health.v1.Health/Check",
            "grpc.health.v1.Health/Watch",
            "grpc.reflection.v1.ServerReflection/ServerReflectionInfo"
    );

    public GrpcMethodPolicyProperties {
        authorities = normalizeAuthorities(authorities);
        authenticatedMethods = normalizeMethods(authenticatedMethods);
        LinkedHashSet<String> methods = new LinkedHashSet<>(
                DEFAULT_PUBLIC_METHODS
        );
        methods.addAll(normalizeMethods(publicMethods));
        publicMethods = Set.copyOf(methods);

        Set<String> overlap = new LinkedHashSet<>(authorities.keySet());
        overlap.retainAll(authenticatedMethods);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException(
                    "gRPC methods cannot be both scoped and authenticated-only: "
                            + overlap
            );
        }
        overlap = new LinkedHashSet<>(authorities.keySet());
        overlap.retainAll(publicMethods);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException(
                    "gRPC methods cannot be both scoped and public: " + overlap
            );
        }
        overlap = new LinkedHashSet<>(authenticatedMethods);
        overlap.retainAll(publicMethods);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException(
                    "gRPC methods cannot be both authenticated-only and public: "
                            + overlap
            );
        }
    }

    private static Map<String, String> normalizeAuthorities(
            Map<String, String> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((method, authority) -> {
            String methodName = required(method, "gRPC method name");
            String canonical = JwtAuthorities.canonicalAuthority(authority);
            if (canonical == null
                    || JwtAuthorities.WILDCARD.equals(canonical)) {
                throw new IllegalArgumentException(
                        "Invalid or wildcard gRPC authority for " + methodName
                );
            }
            if (normalized.putIfAbsent(methodName, canonical) != null) {
                throw new IllegalArgumentException(
                        "Duplicate gRPC method policy: " + methodName
                );
            }
        });
        return Map.copyOf(normalized);
    }

    private static Set<String> normalizeMethods(Set<String> methods) {
        if (methods == null || methods.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String method : methods) {
            normalized.add(required(method, "gRPC method name"));
        }
        return Set.copyOf(normalized);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank() || !value.contains("/")) {
            throw new IllegalArgumentException(
                    name + " must be a non-blank fully-qualified gRPC method"
            );
        }
        return value.trim();
    }
}
