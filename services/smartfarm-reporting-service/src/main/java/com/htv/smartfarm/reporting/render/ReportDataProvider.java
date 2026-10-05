package com.htv.smartfarm.reporting.render;

import com.htv.smartfarm.reporting.domain.ExportJobEntity;
import com.htv.smartfarm.reporting.grpc.ServiceTokenClient;
import com.htv.smartfarm.proto.common.v1.PageRequest;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.livestock.v1.*;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns an export job into a {@link ReportData} table with REAL data where a source service
 * exposes it. LIVESTOCK_TASKS pulls from the livestock service over gRPC (per-service token for
 * the livestock audience). Other report types currently render a descriptive summary table until
 * their source read-models are wired in — the renderer/format path is identical, so adding a new
 * real source is just another branch here.
 */
@Component
public class ReportDataProvider {

    private static final Logger log = LoggerFactory.getLogger(ReportDataProvider.class);

    private static final String LIVESTOCK_AUDIENCE = "smartfarm-livestock";

    private final LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub livestock;
    private final ServiceTokenClient tokens;
    private final long livestockDeadlineMillis;

    public ReportDataProvider(LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub livestock,
                              ServiceTokenClient tokens,
                              @org.springframework.beans.factory.annotation.Value("${smartfarm.reporting.grpc.livestock-deadline:10s}") java.time.Duration deadline) {
        this.livestock = livestock;
        this.tokens = tokens;
        if (deadline == null || deadline.isZero() || deadline.isNegative()) throw new IllegalArgumentException("reporting livestock deadline must be positive");
        this.livestockDeadlineMillis = deadline.toMillis();
    }

    public ReportData build(ExportJobEntity job) {
        return switch (job.getReportType()) {
            case "REPORT_TYPE_LIVESTOCK_TASKS" -> livestockTasks(job);
            // Report types whose real source read-model is not yet wired. These render a
            // descriptive summary row (NOT real source data) by design, and are logged so an
            // operator can tell a placeholder export from a real one. Wiring one is a new branch
            // above + removing it from this set.
            default -> placeholderSummary(job);
        };
    }

    private ReportData placeholderSummary(ExportJobEntity job) {
        log.warn("Report type {} has no real read-model yet; rendering placeholder summary "
                + "(jobTenant={}, farm={}). This export does NOT contain real source data.",
                job.getReportType(), job.getTenantId(), job.getFarmId());
        return summary(job);
    }

    private ReportData livestockTasks(ExportJobEntity job) {
        String token = tokens.tokenFor(LIVESTOCK_AUDIENCE, job.getTenantId(), "reporting");
        var stub = livestock.withDeadlineAfter(livestockDeadlineMillis, TimeUnit.MILLISECONDS)
                .withCallCredentials(new BearerCallCredentials(() -> token));
        var resp = stub.listTasks(ListTasksRequest.newBuilder()
                .setContext(RequestContext.newBuilder().setTenantId(job.getTenantId()).setActorId("reporting"))
                .setFarmId(job.getFarmId())
                .setPage(PageRequest.newBuilder().setPageSize(1000))
                .build());
        var data = new ReportData("Livestock tasks — " + job.getFarmId(),
                List.of("taskId", "title", "type", "status", "assigneeId", "acceptDeadlineAt", "reportDueAt", "completedAt"));
        for (LivestockTask t : resp.getTasksList()) {
            data.addRow(List.of(
                    t.getTaskId(), t.getTitle(), t.getType().name(), t.getStatus().name(),
                    t.getAssigneeId(),
                    t.hasAcceptDeadlineAt() ? Instant.ofEpochSecond(t.getAcceptDeadlineAt().getSeconds()).toString() : "",
                    t.hasReportDueAt() ? Instant.ofEpochSecond(t.getReportDueAt().getSeconds()).toString() : "",
                    t.hasCompletedAt() ? Instant.ofEpochSecond(t.getCompletedAt().getSeconds()).toString() : ""));
        }
        return data;
    }

    private ReportData summary(ExportJobEntity job) {
        var data = new ReportData(job.getReportType() + " — " + job.getFarmId(),
                List.of("report_type", "farm_id", "format", "period_from", "period_to", "generated_at"));
        data.addRow(List.of(
                job.getReportType(), job.getFarmId(), job.getReportFormat(),
                job.getPeriodFrom() == null ? "" : String.valueOf(job.getPeriodFrom()),
                job.getPeriodTo() == null ? "" : String.valueOf(job.getPeriodTo()),
                Instant.now().toString()));
        return data;
    }
}
