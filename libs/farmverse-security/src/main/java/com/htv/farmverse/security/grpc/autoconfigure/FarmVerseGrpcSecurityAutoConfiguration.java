package com.htv.farmverse.security.grpc.autoconfigure;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.htv.farmverse.security.TokenVerifier;
import com.htv.farmverse.security.authorization.AuthorizationVersionWatermarkStore;
import com.htv.farmverse.security.config.FarmVerseSecurityProperties;
import com.htv.farmverse.security.grpc.GrpcMethodPolicy;
import com.htv.farmverse.security.grpc.GrpcMethodPolicyCoverage;
import com.htv.farmverse.security.grpc.GrpcMethodPolicyProperties;
import com.htv.farmverse.security.grpc.GrpcSecurityContext;
import com.htv.farmverse.security.grpc.JwtServerInterceptor;
import com.htv.farmverse.security.grpc.dynamic.GrpcAuthorizationMode;
import com.htv.farmverse.security.grpc.dynamic.GrpcAuthorizationShadowObserver;
import com.htv.farmverse.security.grpc.dynamic.GrpcDynamicAuthorizationEvaluator;
import com.htv.farmverse.security.jwt.JwtSecurityFactory;
import com.htv.farmverse.security.jwt.JwtTokenVerifier;

import io.grpc.BindableService;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@AutoConfiguration
@EnableConfigurationProperties(GrpcMethodPolicyProperties.class)
@ConditionalOnClass({
        BindableService.class,
        GlobalServerInterceptor.class
})
@ConditionalOnBean(BindableService.class)
@ConditionalOnProperty(
        prefix = "farmverse.security.grpc",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class FarmVerseGrpcSecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(
            FarmVerseGrpcSecurityAutoConfiguration.class
    );

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder smartFarmGrpcJwtDecoder(
            FarmVerseSecurityProperties properties
    ) {
        return JwtSecurityFactory.servletDecoder(properties.jwt());
    }

    @Bean
    @ConditionalOnMissingBean(TokenVerifier.class)
    TokenVerifier smartFarmGrpcTokenVerifier(JwtDecoder decoder) {
        return new JwtTokenVerifier(decoder);
    }

    @Bean
    @ConditionalOnMissingBean(GrpcMethodPolicy.class)
    GrpcMethodPolicy smartFarmGrpcMethodPolicy(
            GrpcMethodPolicyProperties properties
    ) {
        GrpcMethodPolicy.Builder builder = GrpcMethodPolicy.builder();
        properties.authorities().forEach(builder::requireAuthority);
        properties.authenticatedMethods().forEach(builder::requireAuthenticated);
        properties.publicMethods().forEach(builder::permitAll);
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "farmverse.security.grpc",
            name = "require-explicit-policy",
            havingValue = "true"
    )
    GrpcMethodPolicyCoverage smartFarmGrpcMethodPolicyCoverage(
            java.util.List<BindableService> services,
            GrpcMethodPolicyProperties properties
    ) {
        return new GrpcMethodPolicyCoverage(services, properties);
    }

    @Bean
    @Order(10)
    @GlobalServerInterceptor
    @ConditionalOnProperty(
            prefix = "farmverse.security.grpc",
            name = "correlation-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    ServerInterceptor smartFarmCorrelationInterceptor(
            FarmVerseSecurityProperties properties
    ) {
        String headerName = properties.correlation().headerName();
        int maximumLength = properties.correlation().maxLength();
        boolean responseHeaderEnabled = properties
                .correlation()
                .isResponseHeaderEnabled();

        Metadata.Key<String> correlationHeader = Metadata.Key.of(
                headerName,
                Metadata.ASCII_STRING_MARSHALLER
        );

        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next
            ) {
                String correlationId = normalizeCorrelationId(
                        headers.get(correlationHeader),
                        maximumLength
                );

                Context context = Context.current().withValue(
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
                                if (responseHeaderEnabled) {
                                    responseHeaders.put(
                                            correlationHeader,
                                            correlationId
                                    );
                                }

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
    @ConditionalOnMissingBean
    GrpcAuthorizationShadowObserver smartFarmGrpcAuthorizationShadowObserver() {
        return new GrpcAuthorizationShadowObserver();
    }

    @Bean
    GrpcAuthorizationMode smartFarmGrpcAuthorizationMode(
            @Value("${farmverse.security.grpc.authorization-mode:OFF}") String value
    ) {
        return GrpcAuthorizationMode.parse(value);
    }

    @Bean
    @Order(20)
    @GlobalServerInterceptor
    @ConditionalOnMissingBean(JwtServerInterceptor.class)
    JwtServerInterceptor smartFarmJwtServerInterceptor(
            TokenVerifier verifier,
            GrpcMethodPolicy policy,
            GrpcAuthorizationMode authorizationMode,
            GrpcAuthorizationShadowObserver shadowObserver,
            ObjectProvider<GrpcDynamicAuthorizationEvaluator> evaluator,
            AuthorizationVersionWatermarkStore watermarks
    ) {
        return new JwtServerInterceptor(
                verifier,
                policy,
                authorizationMode,
                evaluator.getIfAvailable(
                        GrpcDynamicAuthorizationEvaluator::unavailable
                ),
                shadowObserver,
                watermarks
        );
    }

    @Bean
    @Order(30)
    @GlobalServerInterceptor
    @ConditionalOnProperty(
            prefix = "farmverse.security.grpc",
            name = "audit-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    ServerInterceptor smartFarmAuditInterceptor() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
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
                                                System.nanoTime() - startedAt
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

                return next.startCall(auditedCall, headers);
            }
        };
    }

    private static String normalizeCorrelationId(
            String correlationId,
            int maximumLength
    ) {
        if (correlationId == null
                || correlationId.isBlank()
                || correlationId.length() > maximumLength) {
            return UUID.randomUUID().toString();
        }

        return correlationId.trim();
    }
}
