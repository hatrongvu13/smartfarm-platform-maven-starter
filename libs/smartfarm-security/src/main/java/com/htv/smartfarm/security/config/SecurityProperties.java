package com.htv.smartfarm.security.config;

import java.net.URI;
import java.util.Set;

public record SecurityProperties(
        String issuer,
        String jwkSetUri,
        String audience,
        boolean allowLocalHttp
) {
    private static final Set<String> LOOPBACK_HOSTS =
            Set.of("localhost", "127.0.0.1", "[::1]");

    // Giữ tương thích với các nơi đang gọi constructor 3 tham số.
    public SecurityProperties(
            String issuer,
            String jwkSetUri,
            String audience
    ) {
        this(issuer, jwkSetUri, audience, false);
    }

    public SecurityProperties {
        if (isBlank(issuer) || isBlank(jwkSetUri) || isBlank(audience)) {
            throw new IllegalArgumentException(
                    "issuer, jwkSetUri and audience are required"
            );
        }

        URI issuerUri = URI.create(issuer);
        URI jwksUri = URI.create(jwkSetUri);

        validateUri(issuerUri, "issuer");
        validateUri(jwksUri, "jwkSetUri");

        boolean issuerHttps = "https".equalsIgnoreCase(issuerUri.getScheme());
        boolean jwksHttps = "https".equalsIgnoreCase(jwksUri.getScheme());

        if (issuerHttps && jwksHttps) {
            // Cấu hình HTTPS thông thường.
        } else if (allowLocalHttp
                && isLoopbackHttp(issuerUri)
                && isLoopbackHttp(jwksUri)
                && issuerUri.getHost().equalsIgnoreCase(jwksUri.getHost())
                && issuerUri.getPort() == jwksUri.getPort()) {
            // Ngoại lệ chỉ dành cho Identity và JWKS trên cùng loopback host/port.
        } else {
            throw new IllegalArgumentException(
                    "issuer and JWKS must use HTTPS; "
                            + "HTTP is allowed only on the same localhost endpoint "
                            + "when allowLocalHttp=true"
            );
        }
    }

    private static void validateUri(URI uri, String name) {
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || uri.getRawQuery() != null) {
            throw new IllegalArgumentException(
                    name + " must be an absolute URL without "
                            + "userinfo, query or fragment"
            );
        }
    }

    private static boolean isLoopbackHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme())
                && LOOPBACK_HOSTS.contains(uri.getHost().toLowerCase());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}