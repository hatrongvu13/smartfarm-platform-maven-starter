package com.htv.smartfarm.gateway.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wires a {@link WebClient} pointed at the INTERNAL identity service. The identity
 * service is not exposed publicly; only this gateway reaches it. All external auth
 * traffic enters through the gateway and is forwarded here.
 */
@Configuration(proxyBeanMethods = false)
public class IdentityProxyConfig {

    @Bean
    WebClient identityWebClient(
            @Value("${smartfarm.identity.base-url:http://localhost:8092}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
