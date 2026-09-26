package com.htv.smartfarm.readiness.probe;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProbeSettings.class)
public class ProbeConfiguration {
    @Bean
    org.springframework.boot.ApplicationRunner checkProbeProfile(ProbeSettings settings, Environment env) {
        return args -> {
            if (settings.allowLocalHttp() && !env.acceptsProfiles(Profiles.of("dev & !prod")))
                throw new IllegalArgumentException("HTTP probes require dev profile and no prod profile");
        };
    }

    @Bean
    HttpClient readinessHttpClient() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }
}
