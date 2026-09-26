package com.htv.smartfarm.readiness.probe;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartfarm.readiness")
public record ProbeSettings(List<Target> targets, Duration timeout, Duration maxAge, boolean allowLocalHttp) {
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]");

    public ProbeSettings {
        if (targets == null || targets.isEmpty() || targets.size() > 20)
            throw new IllegalArgumentException("1..20 probe targets required");
        targets = List.copyOf(targets);
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(5)) > 0)
            throw new IllegalArgumentException("probe timeout must be 1ms..5s");
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative())
            throw new IllegalArgumentException("maxAge required");
        var names = new java.util.HashSet<String>();
        for (Target target : targets) {
            if (target.name() == null || !target.name().matches("[a-z][a-z0-9-]{1,39}") || !names.add(target.name()))
                throw new IllegalArgumentException("duplicate or invalid target name");
            URI u = URI.create(target.url());
            if (!u.isAbsolute() || u.getHost() == null || u.getRawUserInfo() != null || u.getRawQuery() != null || u.getRawFragment() != null)
                throw new IllegalArgumentException("probe URL must be an absolute URL without userinfo/query/fragment");
            boolean https = "https".equalsIgnoreCase(u.getScheme());
            boolean local = allowLocalHttp && "http".equalsIgnoreCase(u.getScheme()) && LOOPBACK.contains(u.getHost().toLowerCase(java.util.Locale.ROOT));
            if (!https && !local)
                throw new IllegalArgumentException("probe URL requires HTTPS or explicit loopback dev HTTP");
        }
    }

    public record Target(String name, String url) {
    }
}
