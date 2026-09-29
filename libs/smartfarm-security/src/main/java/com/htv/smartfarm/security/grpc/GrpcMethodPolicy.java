package com.htv.smartfarm.security.grpc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Exact full-method-name authorization rules.
 *
 * <p>Methods not declared as public still require a valid token.
 * A method having a configured authority requires both authentication
 * and that exact authority, unless the caller is a super administrator.
 */
public final class GrpcMethodPolicy {

    private static final Set<String> DEFAULT_PUBLIC_METHODS =
            Set.of(
                    "grpc.health.v1.Health/Check",
                    "grpc.health.v1.Health/Watch"
            );

    private final Map<String, String> authorities;
    private final Set<String> publicMethods;

    public GrpcMethodPolicy(
            Map<String, String> authorities,
            Set<String> publicMethods
    ) {
        this.authorities = authorities == null
                ? Map.of()
                : Map.copyOf(authorities);

        this.publicMethods = publicMethods == null
                ? Set.of()
                : Set.copyOf(publicMethods);
    }

    /**
     * Tất cả method đều yêu cầu token, ngoại trừ gRPC Health.
     */
    public static GrpcMethodPolicy authenticatedByDefault() {
        return new GrpcMethodPolicy(
                Map.of(),
                DEFAULT_PUBLIC_METHODS
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isPublic(String fullMethodName) {
        return fullMethodName != null
                && publicMethods.contains(fullMethodName);
    }

    /**
     * Trả về null nếu method chỉ yêu cầu authenticated
     * nhưng không yêu cầu authority cụ thể.
     */
    public String requiredAuthority(String fullMethodName) {
        if (fullMethodName == null) {
            return null;
        }

        return authorities.get(fullMethodName);
    }

    public Map<String, String> authorities() {
        return authorities;
    }

    public Set<String> publicMethods() {
        return publicMethods;
    }

    public static final class Builder {

        private final Map<String, String> authorities =
                new HashMap<>();

        private final Set<String> publicMethods =
                new HashSet<>(DEFAULT_PUBLIC_METHODS);

        private Builder() {
        }

        /**
         * Method yêu cầu JWT hợp lệ và authority cụ thể.
         */
        public Builder requireAuthority(
                String fullMethodName,
                String authority
        ) {
            requireText(
                    fullMethodName,
                    "fullMethodName"
            );

            requireText(
                    authority,
                    "authority"
            );

            publicMethods.remove(fullMethodName);
            authorities.put(
                    fullMethodName,
                    authority
            );

            return this;
        }

        /**
         * Method yêu cầu JWT hợp lệ nhưng không yêu cầu authority riêng.
         */
        public Builder requireAuthenticated(
                String fullMethodName
        ) {
            requireText(
                    fullMethodName,
                    "fullMethodName"
            );

            publicMethods.remove(fullMethodName);
            authorities.remove(fullMethodName);

            return this;
        }

        /**
         * Method không yêu cầu token.
         */
        public Builder permitAll(
                String fullMethodName
        ) {
            requireText(
                    fullMethodName,
                    "fullMethodName"
            );

            authorities.remove(fullMethodName);
            publicMethods.add(fullMethodName);

            return this;
        }

        /**
         * Loại method khỏi danh sách public mặc định.
         */
        public Builder removePublicMethod(
                String fullMethodName
        ) {
            requireText(
                    fullMethodName,
                    "fullMethodName"
            );

            publicMethods.remove(fullMethodName);

            return this;
        }

        /**
         * Xóa toàn bộ public method mặc định, bao gồm gRPC Health.
         */
        public Builder clearPublicMethods() {
            publicMethods.clear();
            return this;
        }

        public GrpcMethodPolicy build() {
            return new GrpcMethodPolicy(
                    authorities,
                    publicMethods
            );
        }

        private static void requireText(
                String value,
                String fieldName
        ) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        fieldName + " must not be blank"
                );
            }
        }
    }
}