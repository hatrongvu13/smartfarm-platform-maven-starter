package com.htv.smartfarm.identity.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.smartfarm.proto.common.v1.RequestContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityRequestMetadataMapperTest {

    private IdentityRequestMetadataMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new IdentityRequestMetadataMapper();
    }

    @Test
    void fromProtoShouldMapEveryField() {
        RequestContext source = RequestContext.newBuilder()
                .setTenantId("tenant-001")
                .setActorId("user-001")
                .setCorrelationId("corr-001")
                .setTraceId("trace-001")
                .setIdempotencyKey("idem-001")
                .build();

        var result = mapper.fromProto(source);

        assertThat(result.tenantId()).isEqualTo("tenant-001");
        assertThat(result.actorId()).isEqualTo("user-001");
        assertThat(result.correlationId()).isEqualTo("corr-001");
        assertThat(result.traceId()).isEqualTo("trace-001");
        assertThat(result.idempotencyKey()).isEqualTo("idem-001");
    }

    @Test
    void fromProtoShouldReturnEmptyMetadataForNull() {
        var result = mapper.fromProto(null);

        assertThat(result.hasTenant()).isFalse();
        assertThat(result.hasActor()).isFalse();
        assertThat(result.hasCorrelationId()).isFalse();
    }

    @Test
    void fromVerifiedValuesShouldNeverTrustBodyTenantAndActor() {
        RequestContext source = RequestContext.newBuilder()
                .setTenantId("untrusted-tenant")
                .setActorId("untrusted-actor")
                .setCorrelationId("corr-001")
                .setTraceId("trace-001")
                .setIdempotencyKey("idem-001")
                .build();

        var result = mapper.fromVerifiedValues(
                source,
                "verified-tenant",
                "verified-actor"
        );

        assertThat(result.tenantId()).isEqualTo("verified-tenant");
        assertThat(result.actorId()).isEqualTo("verified-actor");
        assertThat(result.correlationId()).isEqualTo("corr-001");
        assertThat(result.traceId()).isEqualTo("trace-001");
        assertThat(result.idempotencyKey()).isEqualTo("idem-001");
    }
}
