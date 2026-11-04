package com.htv.farmverse.identity.grpc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.htv.farmverse.contracts.identity.v1.IdentityDirectoryGrpc;

import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.Metadata;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Test: ký access token bằng khóa thật của identity (bean {@link JwtEncoder}) và gắn vào lời gọi gRPC. */
final class GrpcTokens {

    static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcTokens() {
    }

    /** Token người dùng như gateway nhận được (D3-B: farm chuyển tiếp nguyên token này). */
    static String user(JwtEncoder encoder, String issuer, String tenantId, Map<String, Object> overrides) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer).subject("membership-1")
                .audience(List.of("farm-service", "identity-service"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("scope", List.of("openid", "farmverse.api"))
                .claim("token_type", "user")
                .claim("tenant_id", tenantId);
        overrides.forEach((name, value) -> {
            if (value == null) {
                claims.claims(c -> c.remove(name));
            } else {
                claims.claim(name, value);
            }
        });
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims.build()))
                .getTokenValue();
    }

    static CallCredentials bearer(String token) {
        return new CallCredentials() {
            @Override
            public void applyRequestMetadata(RequestInfo info, java.util.concurrent.Executor executor, MetadataApplier applier) {
                Metadata headers = new Metadata();
                headers.put(AUTHORIZATION, "Bearer " + token);
                applier.apply(headers);
            }
        };
    }

    static IdentityDirectoryGrpc.IdentityDirectoryBlockingStub stub(Channel channel, String token) {
        IdentityDirectoryGrpc.IdentityDirectoryBlockingStub stub =
                IdentityDirectoryGrpc.newBlockingStub(channel).withDeadlineAfter(5, TimeUnit.SECONDS);
        return token == null ? stub : stub.withCallCredentials(bearer(token));
    }
}
