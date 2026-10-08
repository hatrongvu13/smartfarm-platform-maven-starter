package com.htv.smartfarm.gateway.reporting;

import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.proto.common.v1.PageRequest;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.reporting.v1.*;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;
import com.htv.smartfarm.security.grpc.GrpcStatusHttpMapping;
import io.grpc.StatusRuntimeException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * DEV REST facade over the reporting gRPC service: request an export, poll its job, list jobs,
 * and fetch a download location. Per-service token for the reporting audience; human actor
 * carried as actor_id. {@code @Profile("dev & !prod")}.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportingDevController {

    private static final String REPORTING_AUDIENCE = "smartfarm-reporting";

    private final ReportingServiceGrpc.ReportingServiceBlockingStub stub;
    private final ServiceTokenClient serviceTokens;

    public ReportingDevController(ReportingServiceGrpc.ReportingServiceBlockingStub reportingStub,
                                  ServiceTokenClient serviceTokens) {
        this.stub = reportingStub;
        this.serviceTokens = serviceTokens;
    }

    public record RequestExport(String farmId, String type, String format, Long fromEpochMs, Long toEpochMs, String templateId) {
    }

    private ReportingServiceGrpc.ReportingServiceBlockingStub authed(String tenant, String actor) {
        String token = serviceTokens.tokenFor(REPORTING_AUDIENCE, tenant, actor);
        return stub.withDeadlineAfter(5, TimeUnit.SECONDS).withCallCredentials(new BearerCallCredentials(() -> token));
    }

    private RequestContext ctx(String tenant, String actor, String key) {
        var b = RequestContext.newBuilder().setTenantId(tenant).setActorId(actor).setCorrelationId(actor);
        if (key != null && !key.isBlank()) b.setIdempotencyKey(key);
        return b.build();
    }

    private <T> Mono<T> call(String op, java.util.concurrent.Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(StatusRuntimeException.class, e -> {
                    var code = e.getStatus().getCode();
                    if (code == io.grpc.Status.Code.UNAUTHENTICATED) serviceTokens.invalidate(REPORTING_AUDIENCE);
                    HttpStatus http = GrpcStatusHttpMapping.httpStatus(code);
                    return new ResponseStatusException(http, "Reporting " + op + " failed: " + code
                            + (e.getStatus().getDescription() == null ? "" : " (" + e.getStatus().getDescription() + ")"));
                });
    }

    private static ReportType type(String n) {
        if (n == null || n.isBlank()) return ReportType.REPORT_TYPE_UNSPECIFIED;
        try { return ReportType.valueOf(n.startsWith("REPORT_TYPE_") ? n : "REPORT_TYPE_" + n); }
        catch (IllegalArgumentException e) { return ReportType.REPORT_TYPE_UNSPECIFIED; }
    }

    private static ReportFormat format(String n) {
        if (n == null || n.isBlank()) return ReportFormat.REPORT_FORMAT_CSV;
        try { return ReportFormat.valueOf(n.startsWith("REPORT_FORMAT_") ? n : "REPORT_FORMAT_" + n); }
        catch (IllegalArgumentException e) { return ReportFormat.REPORT_FORMAT_CSV; }
    }

    private static long ms(com.google.protobuf.Timestamp t) {
        return t.getSeconds() * 1000 + t.getNanos() / 1_000_000;
    }

    private static Map<String, Object> jobView(ExportJob j) {
        var m = new LinkedHashMap<String, Object>();
        m.put("jobId", j.getJobId());
        m.put("farmId", j.getFarmId());
        m.put("type", j.getType().name());
        m.put("format", j.getFormat().name());
        m.put("status", j.getStatus().name());
        if (j.hasCreatedAt()) m.put("createdAt", ms(j.getCreatedAt()));
        if (j.hasCompletedAt()) m.put("completedAt", ms(j.getCompletedAt()));
        if (!j.getErrorCode().isBlank()) m.put("errorCode", j.getErrorCode());
        return m;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_report:write')")
    public Mono<Map<String, Object>> request(@AuthenticationPrincipal Jwt jwt,
                                              @RequestHeader("Idempotency-Key") String key,
                                              @RequestBody RequestExport body) {
        return call("RequestExport", () -> {
            if (body == null || body.farmId() == null || key.isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "farmId and Idempotency-Key required");
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var b = RequestExportRequest.newBuilder().setContext(ctx(tenant, actor, key))
                    .setFarmId(body.farmId()).setType(type(body.type())).setFormat(format(body.format()));
            if (body.templateId() != null) b.setTemplateId(body.templateId());
            if (body.fromEpochMs() != null || body.toEpochMs() != null) {
                var range = com.htv.smartfarm.proto.common.v1.DateRange.newBuilder();
                if (body.fromEpochMs() != null) range.setFrom(com.google.protobuf.Timestamp.newBuilder().setSeconds(body.fromEpochMs() / 1000));
                if (body.toEpochMs() != null) range.setTo(com.google.protobuf.Timestamp.newBuilder().setSeconds(body.toEpochMs() / 1000));
                b.setPeriod(range);
            }
            return jobView(authed(tenant, actor).requestExport(b.build()).getJob());
        });
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_report:read')")
    public Mono<Map<String, Object>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return call("GetExportJob", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = GetExportJobRequest.newBuilder().setContext(ctx(tenant, actor, null)).setJobId(id).build();
            return jobView(authed(tenant, actor).getExportJob(req).getJob());
        });
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_report:read')")
    public Mono<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam String farmId,
                                           @RequestParam(required = false, defaultValue = "50") int limit) {
        return call("ListExportJobs", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = ListExportJobsRequest.newBuilder().setContext(ctx(tenant, actor, null)).setFarmId(farmId)
                    .setPage(PageRequest.newBuilder().setPageSize(limit)).build();
            var resp = authed(tenant, actor).listExportJobs(req);
            List<Map<String, Object>> items = new ArrayList<>();
            for (ExportJob j : resp.getJobsList()) items.add(jobView(j));
            return Map.of("count", items.size(), "jobs", items);
        });
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('SCOPE_report:read')")
    public Mono<Map<String, Object>> download(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return call("GetDownloadLocation", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = GetDownloadLocationRequest.newBuilder().setContext(ctx(tenant, actor, null)).setJobId(id).build();
            var loc = authed(tenant, actor).getDownloadLocation(req);
            var m = new LinkedHashMap<String, Object>();
            m.put("url", loc.getUrl());
            m.put("contentType", loc.getContentType());
            if (loc.hasExpiresAt()) m.put("expiresAt", ms(loc.getExpiresAt()));
            return m;
        });
    }
}
