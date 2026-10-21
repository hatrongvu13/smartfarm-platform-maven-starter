package com.htv.smartfarm.gateway.grpc;

import io.grpc.Metadata;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayGrpcExceptionMapperTest {

    private static final Metadata.Key<String> IDENTITY_ERROR_CODE =
            Metadata.Key.of("identity-error-code", Metadata.ASCII_STRING_MARSHALLER);

    private final GatewayGrpcExceptionMapper mapper = new GatewayGrpcExceptionMapper();

    @Test
    void restPreservesIdentityErrorCodeTrailer() {
        GatewayRestException result = mapper.rest("CreateUser",
                Status.INVALID_ARGUMENT.asRuntimeException(trailers("EMAIL_ALREADY_REGISTERED")));

        assertThat(result.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.code()).isEqualTo("EMAIL_ALREADY_REGISTERED");
    }

    @Test
    void graphQlPreservesIdentityErrorCodeTrailer() {
        GatewayGraphQlException result = mapper.graphQl("CreateUser",
                Status.FAILED_PRECONDITION.asRuntimeException(trailers("MFA_REQUIRED")));

        assertThat(result.classification()).isEqualTo("MFA_REQUIRED");
    }

    @Test
    void fallsBackToGrpcStatusCodeWhenTrailerIsMissingOrBlank() {
        assertThat(mapper.rest("ReadUser",
                Status.NOT_FOUND.asRuntimeException()).code()).isEqualTo("NOT_FOUND");
        assertThat(mapper.graphQl("ReadUser",
                Status.NOT_FOUND.asRuntimeException(trailers(" "))).classification())
                .isEqualTo("NOT_FOUND");
    }

    private static Metadata trailers(String errorCode) {
        Metadata trailers = new Metadata();
        trailers.put(IDENTITY_ERROR_CODE, errorCode);
        return trailers;
    }
}
