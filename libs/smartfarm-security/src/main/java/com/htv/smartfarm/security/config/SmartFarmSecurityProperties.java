package com.htv.smartfarm.security.config;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "smartfarm.security")
@Validated
public record SmartFarmSecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid Grpc grpc,
        @Valid Correlation correlation
) {

    public SmartFarmSecurityProperties {
        if (jwt == null) {
            throw new IllegalArgumentException(
                    "smartfarm.security.jwt is required"
            );
        }

        grpc = grpc == null ? Grpc.defaults() : grpc;
        correlation = correlation == null
                ? Correlation.defaults()
                : correlation;
    }

    public record Jwt(
            @NotNull URI issuer,
            @NotNull URI jwkSetUri,
            @NotEmpty Set<String> audiences,
            boolean allowLocalHttp
    ) {

        public Jwt {
            audiences = normalizeAudiences(audiences);
            SecurityPropertiesValidator.validateJwt(
                    issuer,
                    jwkSetUri,
                    audiences,
                    allowLocalHttp
            );
        }

        private static Set<String> normalizeAudiences(
                Set<String> audiences
        ) {
            if (audiences == null) {
                return Set.of();
            }

            LinkedHashSet<String> normalized = new LinkedHashSet<>();

            for (String audience : audiences) {
                if (audience != null && !audience.isBlank()) {
                    normalized.add(audience.trim());
                }
            }

            return Set.copyOf(normalized);
        }
    }

    public record Grpc(
            Boolean enabled,
            Boolean auditEnabled,
            Boolean correlationEnabled,
            Duration defaultDeadline
    ) {

        private static final Duration DEFAULT_DEADLINE =
                Duration.ofSeconds(5);

        public Grpc {
            enabled = enabled == null ? Boolean.TRUE : enabled;
            auditEnabled = auditEnabled == null
                    ? Boolean.TRUE
                    : auditEnabled;
            correlationEnabled = correlationEnabled == null
                    ? Boolean.TRUE
                    : correlationEnabled;
            defaultDeadline = defaultDeadline == null
                    ? DEFAULT_DEADLINE
                    : defaultDeadline;

            if (defaultDeadline.isZero()
                    || defaultDeadline.isNegative()) {
                throw new IllegalArgumentException(
                        "smartfarm.security.grpc.default-deadline "
                                + "must be positive"
                );
            }
        }

        public static Grpc defaults() {
            return new Grpc(
                    true,
                    true,
                    true,
                    DEFAULT_DEADLINE
            );
        }

        public boolean isEnabled() {
            return Boolean.TRUE.equals(enabled);
        }

        public boolean isAuditEnabled() {
            return Boolean.TRUE.equals(auditEnabled);
        }

        public boolean isCorrelationEnabled() {
            return Boolean.TRUE.equals(correlationEnabled);
        }
    }

    public record Correlation(
            String headerName,
            Integer maxLength,
            Boolean responseHeaderEnabled
    ) {

        private static final String DEFAULT_HEADER_NAME =
                "x-correlation-id";
        private static final int DEFAULT_MAX_LENGTH = 128;

        public Correlation {
            headerName = headerName == null || headerName.isBlank()
                    ? DEFAULT_HEADER_NAME
                    : headerName.trim().toLowerCase();
            maxLength = maxLength == null
                    ? DEFAULT_MAX_LENGTH
                    : maxLength;
            responseHeaderEnabled = responseHeaderEnabled == null
                    ? Boolean.TRUE
                    : responseHeaderEnabled;

            if (!headerName.matches("[a-z0-9][a-z0-9-]{0,62}")) {
                throw new IllegalArgumentException(
                        "smartfarm.security.correlation.header-name "
                                + "is invalid"
                );
            }

            if (maxLength < 16 || maxLength > 512) {
                throw new IllegalArgumentException(
                        "smartfarm.security.correlation.max-length "
                                + "must be between 16 and 512"
                );
            }
        }

        public static Correlation defaults() {
            return new Correlation(
                    DEFAULT_HEADER_NAME,
                    DEFAULT_MAX_LENGTH,
                    true
            );
        }

        public boolean isResponseHeaderEnabled() {
            return Boolean.TRUE.equals(responseHeaderEnabled);
        }
    }
}
