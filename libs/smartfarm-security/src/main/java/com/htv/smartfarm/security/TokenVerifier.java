package com.htv.smartfarm.security;

import java.util.Set;

public interface TokenVerifier {

    VerifiedToken verify(String bearerToken);

    record VerifiedToken(
            String subject,
            Set<String> roles,
            String tenantId
    ) {

        public VerifiedToken {
            roles = roles == null
                    ? Set.of()
                    : Set.copyOf(roles);
        }

        /**
         * Giữ roles() để tương thích ngược.
         * Tập giá trị này thực tế chứa cả ROLE_* và SCOPE_*.
         */
        public Set<String> authorities() {
            return roles;
        }
    }
}