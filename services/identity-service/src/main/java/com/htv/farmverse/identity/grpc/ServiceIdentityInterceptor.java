package com.htv.farmverse.identity.grpc;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import com.htv.farmverse.service.mtls.ServiceIdentityFilter;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * Lớp mTLS thứ hai cho gRPC, giống {@link ServiceIdentityFilter} của REST: TLS đã chứng minh cert do CA của ta ký;
 * interceptor này kiểm <b>service nào</b> đang gọi bằng SAN {@code spiffe://farmverse/<service>} và chỉ cho danh sách
 * được phép gọi các rpc được bảo vệ. Sai thì {@code PERMISSION_DENIED}, trước khi đọc token hay chạm dữ liệu.
 */
public final class ServiceIdentityInterceptor implements ServerInterceptor {

    private final Set<String> allowedServices;
    private final Predicate<String> protectedMethods;

    public ServiceIdentityInterceptor(Set<String> allowedServices, Predicate<String> protectedMethods) {
        this.allowedServices = Set.copyOf(allowedServices);
        this.protectedMethods = protectedMethods;
    }

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {
        if (!protectedMethods.test(call.getMethodDescriptor().getFullMethodName())) {
            return next.startCall(call, headers);
        }
        Optional<String> caller = callerOf(call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION));
        if (caller.isEmpty() || !allowedServices.contains(caller.get())) {
            call.close(Status.PERMISSION_DENIED.withDescription("client certificate of an allowed service required"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
        return next.startCall(call, headers);
    }

    static Optional<String> callerOf(SSLSession session) {
        if (session == null) {
            return Optional.empty();
        }
        try {
            Certificate[] chain = session.getPeerCertificates();
            return chain.length > 0 && chain[0] instanceof X509Certificate certificate
                    ? ServiceIdentityFilter.spiffeId(certificate)
                    : Optional.empty();
        } catch (SSLPeerUnverifiedException e) {
            return Optional.empty();
        }
    }
}
