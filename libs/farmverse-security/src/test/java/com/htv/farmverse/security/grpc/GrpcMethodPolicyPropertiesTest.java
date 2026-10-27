package com.htv.farmverse.security.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerServiceDefinition;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.boot.env.YamlPropertySourceLoader;

class GrpcMethodPolicyPropertiesTest {

    private static final String METHOD = "test.inventory.Inventory/GetItem";

    private static final MethodDescriptor.Marshaller<String> STRINGS =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(String value) {
                    return new ByteArrayInputStream(
                            value.getBytes(StandardCharsets.UTF_8)
                    );
                }

                @Override
                public String parse(InputStream stream) {
                    try {
                        return new String(
                                stream.readAllBytes(),
                                StandardCharsets.UTF_8
                        );
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            };

    @Test
    void canonicalizesBareScopesAndAddsGrpcHealthAsPublicByDefault() {
        var properties = properties(
                Map.of(METHOD, "inventory:read"),
                Set.of(),
                false
        );

        assertThat(properties.authorities())
                .containsEntry(METHOD, "SCOPE_inventory:read");
        assertThat(properties.publicMethods()).contains(
                "grpc.health.v1.Health/Check",
                "grpc.health.v1.Health/Watch"
        );
    }

    @Test
    void rejectsWildcardAndConflictingMethodPolicies() {
        assertThatThrownBy(() -> properties(
                Map.of(METHOD, "SCOPE_*"),
                Set.of(),
                false
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new GrpcMethodPolicyProperties(
                Map.of(METHOD, "inventory:read"),
                Set.of(METHOD),
                Set.of(),
                false
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesThatEveryRegisteredMethodHasOneExplicitClassification() {
        var valid = properties(
                Map.of(METHOD, "inventory:read"),
                Set.of(),
                true
        );
        new GrpcMethodPolicyCoverage(List.of(service()), valid)
                .afterSingletonsInstantiated();

        var missing = properties(Map.of(), Set.of(), true);
        assertThatThrownBy(() -> new GrpcMethodPolicyCoverage(
                List.of(service()),
                missing
        ).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(METHOD);
    }

    @Test
    void rejectsPolicyEntriesForMethodsNotRegisteredByTheServer() {
        var stale = properties(
                Map.of("test.inventory.Inventory/RemovedMethod", "inventory:read"),
                Set.of(),
                true
        );

        assertThatThrownBy(() -> new GrpcMethodPolicyCoverage(
                List.of(service()),
                stale
        ).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown policy methods");
    }

    @Test
    void bindsYamlMethodKeysWithDotsAndSlashesAsLiteralMapKeys() throws Exception {
        String yaml = """
                farmverse:
                  security:
                    grpc:
                      authorities:
                        "[test.inventory.Inventory/GetItem]": inventory:read
                      authenticated-methods:
                        - test.inventory.Inventory/Ping
                """;
        var resource = new ByteArrayResource(
                yaml.getBytes(StandardCharsets.UTF_8)
        );
        var source = new YamlPropertySourceLoader()
                .load("grpc-method-policy-test", resource)
                .get(0);

        var properties = new Binder(
                ConfigurationPropertySources.from(source)
        ).bind(
                "farmverse.security.grpc",
                Bindable.of(GrpcMethodPolicyProperties.class)
        ).get();

        assertThat(properties.authorities())
                .containsEntry(METHOD, "SCOPE_inventory:read");
        assertThat(properties.authenticatedMethods())
                .contains("test.inventory.Inventory/Ping");
    }

    private static GrpcMethodPolicyProperties properties(
            Map<String, String> authorities,
            Set<String> authenticated,
            boolean requireExplicit
    ) {
        return new GrpcMethodPolicyProperties(
                authorities,
                authenticated,
                Set.of(),
                requireExplicit
        );
    }

    private static BindableService service() {
        var descriptor = MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(METHOD)
                .setRequestMarshaller(STRINGS)
                .setResponseMarshaller(STRINGS)
                .build();
        var definition = ServerServiceDefinition.builder(
                        "test.inventory.Inventory"
                )
                .addMethod(
                        descriptor,
                        (ServerCall<String, String> call,
                                io.grpc.Metadata headers) ->
                                new ServerCall.Listener<>() {
                                }
                )
                .build();
        return () -> definition;
    }
}
