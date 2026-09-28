package com.htv.smartfarm.reporting.worker;

import com.htv.smartfarm.reporting.domain.ExportJobEntity;
import com.htv.smartfarm.reporting.domain.ExportJobJpaRepository;
import com.htv.smartfarm.reporting.domain.ReportingSettings;
import com.htv.smartfarm.reporting.render.ReportData;
import com.htv.smartfarm.reporting.render.ReportDataProvider;
import com.htv.smartfarm.reporting.render.ReportRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Async export worker: picks QUEUED jobs and produces the artifact from REAL data. It asks the
 * {@link ReportDataProvider} for a {@link ReportData} table (which pulls from the owning service
 * over gRPC where available), then the {@link ReportRenderer} writes it as CSV/XLSX/PDF into the
 * configured output directory, and the job is marked COMPLETED with the file path.
 *
 * <p>Single-instance DEV worker. Gated on {@code smartfarm.reporting.worker.enabled} (default true).
 */
@Component
@ConditionalOnProperty(prefix = "smartfarm.reporting.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExportJobWorker {

    private static final Logger AUDIT = LoggerFactory.getLogger("smartfarm.audit.reporting");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ExportJobJpaRepository jobs;
    private final ReportingSettings settings;
    private final ReportDataProvider provider;
    private final ReportRenderer renderer;

    public ExportJobWorker(ExportJobJpaRepository jobs, ReportingSettings settings,
                           ReportDataProvider provider, ReportRenderer renderer) {
        this.jobs = jobs;
        this.settings = settings;
        this.provider = provider;
        this.renderer = renderer;
    }

    @Scheduled(fixedDelayString = "${smartfarm.reporting.worker.poll-ms:5000}")
    @Transactional
    public void process() {
        List<ExportJobEntity> queued = jobs.queued(PageRequest.of(0, 10));
        for (ExportJobEntity job : queued) {
            job.markRunning();
            jobs.save(job);
            try {
                Path file = render(job);
                job.markCompleted(file.toAbsolutePath().toString(), contentType(job.getReportFormat()), System.currentTimeMillis());
                jobs.save(job);
                AUDIT.info("export_completed job_id={} tenant={} file={}", job.getId(), job.getTenantId(), file.getFileName());
            } catch (Exception e) {
                Throwable root = e; while (root.getCause() != null) root = root.getCause();
                String detail = root.getClass().getSimpleName() + ": " + String.valueOf(root.getMessage());
                // Column is error_code VARCHAR(100); ddl-auto=update won't widen an existing
                // column, so cap to 90 to hold "RENDER_FAILED <Exception>: <start of msg>".
                job.markFailed(truncate("RENDER_FAILED " + detail, 90), System.currentTimeMillis());
                jobs.save(job);
                AUDIT.warn("export_failed job_id={} tenant={} format={} reason={}",
                        job.getId(), job.getTenantId(), job.getReportFormat(), detail, e);
            }
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private Path render(ExportJobEntity job) throws IOException {
        Path dir = Path.of(settings.getOutputDir());
        Files.createDirectories(dir);
        String ext = extension(job.getReportFormat());
        String name = job.getReportType().toLowerCase().replace("report_type_", "")
                + "-" + job.getFarmId() + "-" + STAMP.format(Instant.now().atZone(java.time.ZoneOffset.UTC)) + "." + ext;
        Path file = dir.resolve(sanitize(name));
        ReportData data = provider.build(job);          // REAL data (gRPC) where available
        renderer.render(job.getReportFormat(), data, file);
        return file;
    }

    private static String extension(String format) {
        return switch (format) {
            case "REPORT_FORMAT_PDF" -> "pdf";
            case "REPORT_FORMAT_XLSX" -> "xlsx";
            default -> "csv";
        };
    }

    private static String contentType(String format) {
        return switch (format) {
            case "REPORT_FORMAT_PDF" -> "application/pdf";
            case "REPORT_FORMAT_XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "text/csv";
        };
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
