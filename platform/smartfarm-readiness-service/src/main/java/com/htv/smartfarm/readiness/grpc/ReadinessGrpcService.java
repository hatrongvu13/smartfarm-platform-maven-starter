package com.htv.smartfarm.readiness.grpc;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.proto.readiness.v1.*;
import com.htv.smartfarm.readiness.probe.ReadinessMonitor;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.time.Instant;

import org.springframework.stereotype.Service;

@Service
public class ReadinessGrpcService extends PlatformReadinessServiceGrpc.PlatformReadinessServiceImplBase {
    private final ReadinessMonitor monitor;

    public ReadinessGrpcService(ReadinessMonitor monitor) {
        this.monitor = monitor;
    }

    private static Timestamp timestamp(Instant i) {
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }

    @Override
    public void getPlatformReadiness(GetPlatformReadinessRequest request, StreamObserver<GetPlatformReadinessResponse> observer) {
        String tenant = GrpcSecurityContext.TENANT.get();
        if (tenant == null || tenant.isBlank()) {
            observer.onError(Status.UNAUTHENTICATED.asRuntimeException());
            return;
        }
        if (request.hasContext() && !request.getContext().getTenantId().isBlank() && !tenant.equals(request.getContext().getTenantId())) {
            observer.onError(Status.PERMISSION_DENIED.asRuntimeException());
            return;
        }
        var snapshot = monitor.snapshot();
        var result = GetPlatformReadinessResponse.newBuilder().setOverall(snapshot.overall()).setCheckedAt(timestamp(snapshot.checkedAt()));
        for (var c : snapshot.components())
            result.addComponents(ComponentHealth.newBuilder().setServiceName(c.serviceName())
                    .setStatus(c.status()).setDetail(c.detail()).setCheckedAt(timestamp(c.checkedAt())));
        observer.onNext(result.build());
        observer.onCompleted();
    }
}
