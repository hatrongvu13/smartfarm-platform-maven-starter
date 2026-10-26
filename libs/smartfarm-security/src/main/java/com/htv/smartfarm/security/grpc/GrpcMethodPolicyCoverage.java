package com.htv.smartfarm.security.grpc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.grpc.BindableService;

import org.springframework.beans.factory.SmartInitializingSingleton;

public final class GrpcMethodPolicyCoverage implements SmartInitializingSingleton {

    private static final Set<String> DEFAULT_INFRASTRUCTURE_METHODS = Set.of(
            "grpc.health.v1.Health/Check",
            "grpc.health.v1.Health/Watch",
            "grpc.reflection.v1.ServerReflection/ServerReflectionInfo"
    );

    private final List<BindableService> services;
    private final GrpcMethodPolicyProperties properties;

    public GrpcMethodPolicyCoverage(
            List<BindableService> services,
            GrpcMethodPolicyProperties properties
    ) {
        this.services = List.copyOf(services);
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<String> methods = services.stream()
                .flatMap(service -> service.bindService().getMethods().stream())
                .map(definition -> definition.getMethodDescriptor()
                        .getFullMethodName())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> classified = new LinkedHashSet<>(
                properties.authorities().keySet()
        );
        classified.addAll(properties.authenticatedMethods());
        classified.addAll(properties.publicMethods());

        Set<String> unclassified = new LinkedHashSet<>(methods);
        unclassified.removeAll(classified);
        Set<String> unknown = new LinkedHashSet<>(classified);
        unknown.removeAll(methods);
        unknown.removeAll(DEFAULT_INFRASTRUCTURE_METHODS);

        if (!unclassified.isEmpty() || !unknown.isEmpty()) {
            throw new IllegalStateException(
                    "gRPC security policy coverage failed; unclassified methods="
                            + unclassified + ", unknown policy methods=" + unknown
            );
        }
    }
}
