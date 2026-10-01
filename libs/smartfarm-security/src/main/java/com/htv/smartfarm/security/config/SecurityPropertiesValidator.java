package com.htv.smartfarm.security.config;

import java.net.URI;
import java.util.Set;

final class SecurityPropertiesValidator {

    private static final Set<String> LOOPBACK_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "::1"
    );

    private SecurityPropertiesValidator() {
    }

    static void validateJwt(
            URI issuer,
            URI jwkSetUri,
            Set<String> audiences,
            boolean allowLocalHttp
    ) {
        validateUri(issuer, "issuer");
        validateUri(jwkSetUri, "jwk-set-uri");

        if (audiences == null || audiences.isEmpty()) {
            throw new IllegalArgumentException(
                    "smartfarm.security.jwt.audiences must not be empty"
            );
        }

        boolean issuerHttps = isHttps(issuer);
        boolean jwksHttps = isHttps(jwkSetUri);

        if (issuerHttps && jwksHttps) {
            return;
        }

        if (allowLocalHttp
                && isLoopbackHttp(issuer)
                && isLoopbackHttp(jwkSetUri)
                && sameEndpoint(issuer, jwkSetUri)) {
            return;
        }

        throw new IllegalArgumentException(
                "JWT issuer and JWKS must use HTTPS; HTTP is allowed "
                        + "only on the same loopback endpoint when "
                        + "allow-local-http=true"
        );
    }

    private static void validateUri(
            URI uri,
            String propertyName
    ) {
        if (uri == null
                || !uri.isAbsolute()
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "smartfarm.security.jwt."
                            + propertyName
                            + " must be an absolute URL without "
                            + "userinfo, query or fragment"
            );
        }
    }

    private static boolean isHttps(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme());
    }

    private static boolean isLoopbackHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme())
                && LOOPBACK_HOSTS.contains(normalizeHost(uri));
    }

    private static boolean sameEndpoint(
            URI first,
            URI second
    ) {
        return normalizeHost(first).equals(normalizeHost(second))
                && effectivePort(first) == effectivePort(second);
    }

    private static String normalizeHost(URI uri) {
        String host = uri.getHost().toLowerCase();

        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }

        return host;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }

        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }

        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }

        return -1;
    }
}
