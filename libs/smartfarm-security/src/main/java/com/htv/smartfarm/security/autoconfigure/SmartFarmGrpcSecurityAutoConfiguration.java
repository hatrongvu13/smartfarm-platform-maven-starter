package com.htv.smartfarm.security.grpc.autoconfigure;

import com.htv.smartfarm.security.TokenVerifier;
import com.htv.smartfarm.security.config.SecurityProperties;
import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;
import com.htv.smartfarm.security.grpc.JwtServerInterceptor;
import com.htv.smartfarm.security.jwt.JwtSecurityFactory;
import com.htv.smartfarm.security.jwt.JwtTokenVerifier;

import io.grpc.BindableService;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@AutoConfiguration
@ConditionalOnClass({
        BindableService.class,
        GlobalServerInterceptor.class
})
@ConditionalOnBean(BindableService.class)
@ConditionalOnProperty(
        prefix = "smartfarm.security.grpc",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SmartFarmGrpcSecurityAutoConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(
                    SmartFarmGrpcSecurityAutoConfiguration.class
            );

    private static final Metadata.Key<String> CORRELATION_HEADER =
            Metadata.Key.of(
                    "x-correlation-id",
                    Metadata.ASCII_STRING_MARSHALLER
            );

    private static final int MAX_CORRELATION_LENGTH = 128;

    @Bean
    @ConditionalOnMissingBean(SecurityProperties.class)
    SecurityProperties smartFarmSecurityProperties(
            Environment environment
    ) {
        boolean allowLocalHttp =
                environment.acceptsProfiles(
                        Profiles.of("dev & !prod")
                )
                        && environment.getProperty(
                        "smartfarm.security.allow-local-http",
                        Boolean.class,
                        false
                );

        return new SecurityProperties(
                environment.getRequiredProperty(
                        "smartfarm.security.issuer"
                ),
                environment.getRequiredProperty(
                        "smartfarm.security.jwk-set-uri"
                ),
                environment.getRequiredProperty(
                        "smartfarm.security.audience"
                ),
                allowLocalHttp
        );
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder smartFarmGrpcJwtDecoder(
            SecurityProperties properties
    ) {
        return JwtSecurityFactory.servletDecoder(properties);
    }

    @Bean
    @ConditionalOnMissingBean(TokenVerifier.class)
    TokenVerifier smartFarmGrpcTokenVerifier(
            JwtDecoder decoder
    ) {
        return new JwtTokenVerifier(decoder);
    }

    @Bean
    @ConditionalOnMissingBean(GrpcMethodPolicy.class)
    GrpcMethodPolicy smartFarmGrpcMethodPolicy() {
        return GrpcMethodPolicy.authenticatedByDefault();
    }

    @Bean
    @Order(10)
    @GlobalServerInterceptor
    @ConditionalOnProperty(
            prefix = "smartfarm.security.grpc.correlation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    ServerInterceptor smartFarmCorrelationInterceptor() {
        return new ServerInterceptor() {

            @Override
            public <ReqT, RespT>
            ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next
            ) {
                String correlationId = normalizeCorrelationId(
                        headers.get(CORRELATION_HEADER)
                );

                Context context = Context.current()
                        .withValue(
                                GrpcSecurityContext.CORRELATION_ID,
                                correlationId
                        );

                ServerCall<ReqT, RespT> responseCall =
                        new ForwardingServerCall
                                .SimpleForwardingServerCall<>(call) {

                            @Override
                            public void sendHeaders(
                                    Metadata responseHeaders
                            ) {
                                responseHeaders.put(
                                        CORRELATION_HEADER,
                                        correlationId
                                );

                                super.sendHeaders(responseHeaders);
                            }
                        };

                return Contexts.interceptCall(
                        context,
                        responseCall,
                        headers,
                        next
                );
            }
        };
    }

    @Bean
    @Order(20)
    @GlobalServerInterceptor
    @ConditionalOnMissingBean(JwtServerInterceptor.class)
    JwtServerInterceptor smartFarmJwtServerInterceptor(
            TokenVerifier verifier,
            GrpcMethodPolicy policy
    ) {
        return new JwtServerInterceptor(
                verifier,
                policy
        );
    }

    @Bean
    @Order(30)
    @GlobalServerInterceptor
    @ConditionalOnProperty(
            prefix = "smartfarm.security.grpc.audit",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    ServerInterceptor smartFarmAuditInterceptor() {
        return new ServerInterceptor() {

            @Override
            public <ReqT, RespT>
            ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next
            ) {
                long startedAt = System.nanoTime();

                String method = call
                        .getMethodDescriptor()
                        .getFullMethodName();

                String correlationId =
                        GrpcSecurityContext.CORRELATION_ID.get();

                ServerCall<ReqT, RespT> auditedCall =
                        new ForwardingServerCall
                                .SimpleForwardingServerCall<>(call) {

                            @Override
                            public void close(
                                    Status status,
                                    Metadata trailers
                            ) {
                                long durationMs =
                                        TimeUnit.NANOSECONDS.toMillis(
                                                System.nanoTime()
                                                        - startedAt
                                        );

                                log.info(
                                        "grpc_method={} status={} "
                                                + "duration_ms={} "
                                                + "correlation_id={}",
                                        method,
                                        status.getCode(),
                                        durationMs,
                                        correlationId
                                );

                                super.close(status, trailers);
                            }
                        };

                return next.startCall(
                        auditedCall,
                        headers
                );
            }
        };
    }

    private static String normalizeCorrelationId(
            String correlationId
    ) {
        if (correlationId == null
                || correlationId.isBlank()
                || correlationId.length()
                > MAX_CORRELATION_LENGTH) {
            return UUID.randomUUID().toString();
        }

        return correlationId.trim();
    }
}