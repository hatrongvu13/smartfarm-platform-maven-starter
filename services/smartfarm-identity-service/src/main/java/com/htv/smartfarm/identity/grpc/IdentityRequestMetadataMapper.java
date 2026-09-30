package com.htv.smartfarm.identity.grpc;

import com.htv.smartfarm.common.context.RequestMetadata;
import com.htv.smartfarm.proto.common.v1.RequestContext;

import org.springframework.stereotype.Component;

/**
 * Maps the protobuf transport context to the transport-neutral common-kernel model.
 *
 * <p>This mapper does not authenticate tenantId or actorId. GrpcRequestSecurity must
 * validate the protobuf values against the authenticated gRPC security context first.
 */
@Component
public final class IdentityRequestMetadataMapper {

    public RequestMetadata fromProto(RequestContext source) {
        if (source == null) {
            return RequestMetadata.empty();
        }

        return new RequestMetadata(
                source.getTenantId(),
                source.getActorId(),
                source.getCorrelationId(),
                source.getTraceId(),
                source.getIdempotencyKey()
        );
    }

    public RequestMetadata fromVerifiedValues(
            RequestContext source,
            String verifiedTenantId,
            String verifiedActorId
    ) {
        if (source == null) {
            return new RequestMetadata(
                    verifiedTenantId,
                    verifiedActorId,
                    null,
                    null,
                    null
            );
        }

        return new RequestMetadata(
                verifiedTenantId,
                verifiedActorId,
                source.getCorrelationId(),
                source.getTraceId(),
                source.getIdempotencyKey()
        );
    }
}
