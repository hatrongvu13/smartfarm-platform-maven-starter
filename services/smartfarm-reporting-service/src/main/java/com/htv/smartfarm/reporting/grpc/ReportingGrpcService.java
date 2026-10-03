package com.htv.smartfarm.reporting.grpc;

import com.htv.smartfarm.reporting.domain.ExportJobEntity;
import com.htv.smartfarm.reporting.domain.ReportingCommands;
import com.htv.smartfarm.reporting.domain.ReportingSettings;
import com.htv.smartfarm.reporting.download.DownloadLocationResolver;
import com.htv.smartfarm.proto.reporting.v1.*;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;
import com.google.protobuf.Timestamp;

import java.util.function.Supplier;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

/**
 * gRPC surface for reporting. Internal service — reachable only via gRPC behind the
 * gateway/service-token boundary. Tenant comes from the verified call context.
 */
@Service
public class ReportingGrpcService extends ReportingServiceGrpc.ReportingServiceImplBase {

    private final ReportingCommands commands;
    private final ReportingSettings settings;
    private final DownloadLocationResolver downloadLocationResolver;

    public ReportingGrpcService(ReportingCommands commands, ReportingSettings settings,
                                DownloadLocationResolver downloadLocationResolver) {
        this.commands = commands;
        this.settings = settings;
        this.downloadLocationResolver = downloadLocationResolver;
    }

    private static String tenant() {
        String t = GrpcSecurityContext.TENANT.get();
        if (t == null || t.isBlank()) throw Status.UNAUTHENTICATED.asRuntimeException();
        return t;
    }

    private static String actor() {
        return GrpcSecurityContext.SUBJECT.get();
    }

    private static <T> void respond(StreamObserver<T> out, Supplier<T> action) {
        try {
            out.onNext(action.get());
            out.onCompleted();
        } catch (IllegalArgumentException e) {
            out.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (SecurityException e) {
            out.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
        } catch (io.grpc.StatusRuntimeException e) {
            out.onError(e);
        } catch (RuntimeException e) {
            out.onError(Status.INTERNAL.withDescription("reporting operation failed").asRuntimeException());
        }
    }

    private static Timestamp ts(Long ms) {
        long m = ms == null ? 0 : ms;
        return Timestamp.newBuilder().setSeconds(Math.floorDiv(m, 1000)).setNanos((int) Math.floorMod(m, 1000) * 1_000_000).build();
    }

    private static ReportType type(String n) {
        try { return n == null ? ReportType.REPORT_TYPE_UNSPECIFIED : ReportType.valueOf(n); }
        catch (IllegalArgumentException e) { return ReportType.REPORT_TYPE_UNSPECIFIED; }
    }

    private static ReportFormat format(String n) {
        try { return n == null ? ReportFormat.REPORT_FORMAT_UNSPECIFIED : ReportFormat.valueOf(n); }
        catch (IllegalArgumentException e) { return ReportFormat.REPORT_FORMAT_UNSPECIFIED; }
    }

    private static ExportStatus status(String n) {
        try { return n == null ? ExportStatus.EXPORT_STATUS_UNSPECIFIED : ExportStatus.valueOf(n); }
        catch (IllegalArgumentException e) { return ExportStatus.EXPORT_STATUS_UNSPECIFIED; }
    }

    private static ExportJob toProto(ExportJobEntity e) {
        var b = ExportJob.newBuilder()
                .setJobId(e.getId()).setFarmId(e.getFarmId())
                .setType(type(e.getReportType())).setFormat(format(e.getReportFormat()))
                .setStatus(status(e.getStatus()))
                .setCreatedAt(ts(e.getCreatedAt()));
        if (e.getCompletedAt() != null) b.setCompletedAt(ts(e.getCompletedAt()));
        if (e.getErrorCode() != null) b.setErrorCode(e.getErrorCode());
        if (e.getTemplateId() != null) b.setTemplateVersion(e.getTemplateId());
        return b.build();
    }

    @Override
    public void requestExport(RequestExportRequest req, StreamObserver<ExportJobResponse> out) {
        respond(out, () -> ExportJobResponse.newBuilder().setJob(toProto(commands.requestExport(tenant(), actor(), req))).build());
    }

    @Override
    public void getExportJob(GetExportJobRequest req, StreamObserver<ExportJobResponse> out) {
        respond(out, () -> {
            var job = commands.get(tenant(), req.getJobId());
            if (job == null) throw Status.NOT_FOUND.withDescription("export job not found").asRuntimeException();
            return ExportJobResponse.newBuilder().setJob(toProto(job)).build();
        });
    }

    @Override
    public void listExportJobs(ListExportJobsRequest req, StreamObserver<ListExportJobsResponse> out) {
        respond(out, () -> {
            int limit = req.hasPage() && req.getPage().getPageSize() > 0 ? req.getPage().getPageSize() : 50;
            var b = ListExportJobsResponse.newBuilder();
            for (ExportJobEntity e : commands.list(tenant(), req.getFarmId(), limit)) b.addJobs(toProto(e));
            return b.build();
        });
    }

    @Override
    public void getDownloadLocation(GetDownloadLocationRequest req, StreamObserver<DownloadLocationResponse> out) {
        respond(out, () -> {
            var job = commands.get(tenant(), req.getJobId());
            if (job == null) throw Status.NOT_FOUND.withDescription("export job not found").asRuntimeException();
            if (!"EXPORT_STATUS_COMPLETED".equals(job.getStatus()) || job.getFilePath() == null)
                throw Status.FAILED_PRECONDITION.withDescription("job not completed").asRuntimeException();
            // Download location is resolved through a swappable seam: the default returns a local
            // file:// reference; a production bean returns a presigned object-storage URL. The
            // response contract (url, contentType, expiresAt) is identical either way.
            DownloadLocationResolver.Location loc =
                    downloadLocationResolver.resolve(job, settings.getDownloadTtlSeconds());
            return DownloadLocationResponse.newBuilder()
                    .setUrl(loc.url())
                    .setContentType(loc.contentType())
                    .setExpiresAt(ts(loc.expiresAtEpochMillis()))
                    .build();
        });
    }
}
