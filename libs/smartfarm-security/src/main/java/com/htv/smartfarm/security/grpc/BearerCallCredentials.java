package com.htv.smartfarm.security.grpc;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class BearerCallCredentials
        extends CallCredentials {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of(
                    "authorization",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    private static final Metadata.Key<String> TENANT =
            Metadata.Key.of(
                    "x-tenant-id",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    private static final Metadata.Key<String> CORRELATION_ID =
            Metadata.Key.of(
                    "x-correlation-id",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    private final Supplier<String> tokenSupplier;
    private final Supplier<String> tenantSupplier;
    private final Supplier<String> correlationSupplier;

    public BearerCallCredentials(
            Supplier<String> tokenSupplier
    ) {
        this(
                tokenSupplier,
                () -> null,
                () -> UUID.randomUUID().toString()
        );
    }

    public BearerCallCredentials(
            Supplier<String> tokenSupplier,
            Supplier<String> tenantSupplier,
            Supplier<String> correlationSupplier
    ) {
        this.tokenSupplier = tokenSupplier;
        this.tenantSupplier = tenantSupplier;
        this.correlationSupplier = correlationSupplier;
    }

    @Override
    public void applyRequestMetadata(
            RequestInfo requestInfo,
            Executor executor,
            MetadataApplier applier
    ) {
        executor.execute(() -> {
            try {
                String token = normalize(
                        tokenSupplier.get()
                );

                if (token == null) {
                    applier.fail(
                            Status.UNAUTHENTICATED.withDescription(
                                    "Service token is unavailable"
                            )
                    );
                    return;
                }

                Metadata metadata = new Metadata();

                metadata.put(
                        AUTHORIZATION,
                        "Bearer " + removeBearerPrefix(token)
                );

                String tenantId = normalize(
                        tenantSupplier.get()
                );

                if (tenantId != null) {
                    metadata.put(TENANT, tenantId);
                }

                String correlationId = normalize(
                        correlationSupplier.get()
                );

                if (correlationId == null
                        || correlationId.length() > 128) {
                    correlationId =
                            UUID.randomUUID().toString();
                }

                metadata.put(
                        CORRELATION_ID,
                        correlationId
                );

                applier.apply(metadata);
            } catch (RuntimeException exception) {
                applier.fail(
                        Status.UNAUTHENTICATED
                                .withDescription(
                                        "Unable to apply "
                                                + "gRPC credentials"
                                )
                                .withCause(exception)
                );
            }
        });
    }

    private String removeBearerPrefix(String token) {
        if (token.regionMatches(
                true,
                0,
                "Bearer ",
                0,
                7
        )) {
            return token.substring(7).trim();
        }

        return token;
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    @Override
    public void thisUsesUnstableApi() {
        // Required by the gRPC CallCredentials API.
    }
}