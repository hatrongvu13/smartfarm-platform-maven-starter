package com.htv.smartfarm.reporting.domain;

import com.htv.smartfarm.proto.reporting.v1.RequestExportRequest;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write/read model for export jobs. RequestExport is idempotent on the request's idempotency
 * key: a retried request returns the existing job instead of queueing a duplicate. Tenant is
 * the verified caller tenant, never the request body.
 */
@Service
public class ReportingCommands {

    private static final Logger AUDIT = LoggerFactory.getLogger("smartfarm.audit.reporting");

    private final ExportJobJpaRepository jobs;

    public ReportingCommands(ExportJobJpaRepository jobs) {
        this.jobs = jobs;
    }

    private static void require(boolean ok, String msg) {
        if (!ok) throw new IllegalArgumentException(msg);
    }

    private static String safe(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    @Transactional
    public ExportJobEntity requestExport(String tenant, String actor, RequestExportRequest req) {
        require(tenant != null && !tenant.isBlank(), "authenticated tenant required");
        require(req.hasContext() && !req.getContext().getIdempotencyKey().isBlank(), "idempotency_key required");
        require(!req.getFarmId().isBlank(), "farm_id required");
        String key = req.getContext().getIdempotencyKey();
        require(key.length() <= 128, "idempotency_key too long");
        if (!req.getContext().getTenantId().isBlank() && !tenant.equals(req.getContext().getTenantId()))
            throw new SecurityException("tenant mismatch");

        var prior = jobs.findByTenantIdAndIdempotencyKey(tenant, key).orElse(null);
        if (prior != null) {
            AUDIT.info("export_request_idempotent_hit tenant={} actor_id={} job_id={}",
                    tenant, safe(req.getContext().getActorId()), prior.getId());
            return prior;
        }
        String type = req.getType().name();
        String format = req.getFormat() == com.htv.smartfarm.proto.reporting.v1.ReportFormat.REPORT_FORMAT_UNSPECIFIED
                ? "REPORT_FORMAT_CSV" : req.getFormat().name();
        Long from = req.hasPeriod() && req.getPeriod().hasFrom() ? req.getPeriod().getFrom().getSeconds() * 1000 : null;
        Long to = req.hasPeriod() && req.getPeriod().hasTo() ? req.getPeriod().getTo().getSeconds() * 1000 : null;
        var e = new ExportJobEntity(UUID.randomUUID().toString(), tenant, req.getFarmId(), type, format,
                "EXPORT_STATUS_QUEUED", from, to, emptyToNull(req.getTemplateId()), System.currentTimeMillis(), key);
        jobs.save(e);
        AUDIT.info("export_requested tenant={} actor_id={} job_id={} farm_id={} type={} format={}",
                tenant, safe(req.getContext().getActorId()), e.getId(), e.getFarmId(), type, format);
        return e;
    }

    @Transactional(readOnly = true)
    public ExportJobEntity get(String tenant, String id) {
        return jobs.findByTenantIdAndId(tenant, id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ExportJobEntity> list(String tenant, String farm, int limit) {
        require(farm != null && !farm.isBlank(), "farm_id required");
        return jobs.list(tenant, farm, PageRequest.of(0, Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200))));
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
