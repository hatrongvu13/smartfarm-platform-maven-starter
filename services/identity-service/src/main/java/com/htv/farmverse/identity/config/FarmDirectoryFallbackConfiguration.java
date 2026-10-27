package com.htv.farmverse.identity.config;

import java.lang.reflect.Proxy;

import com.htv.farmverse.identity.authorization.application.FarmDirectoryPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fail-closed startup fallback for deployments without a farm-directory adapter.
 *
 * This bean must not be treated as a production implementation. It exists so
 * unrelated Identity capabilities can start while farm-scope integration is
 * being wired. Every invocation fails explicitly rather than authorizing an
 * unknown farm.
 */
@Configuration(proxyBeanMethods = false)
public class FarmDirectoryFallbackConfiguration {

    @Bean
    @ConditionalOnMissingBean(FarmDirectoryPort.class)
    FarmDirectoryPort unavailableFarmDirectoryPort() {
        return (FarmDirectoryPort) Proxy.newProxyInstance(
                FarmDirectoryPort.class.getClassLoader(),
                new Class<?>[] {FarmDirectoryPort.class},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "UnavailableFarmDirectoryPort";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    throw new IllegalStateException(
                            "FarmDirectoryPort is not configured; farm-scope "
                                    + "operation denied: " + method.getName()
                    );
                }
        );
    }
}
