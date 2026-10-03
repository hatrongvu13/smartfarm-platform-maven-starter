# CHUNK-09 — services/smartfarm-reporting-service

**Scope:** async export jobs rendered to CSV/XLSX/PDF, with one real cross-service
data source (livestock tasks over gRPC).

**Graph manifest (graphslice --summary):**
- 111 nodes · inbound 0 / outbound 166
- annotations: {Service:8, Entity:3, GrpcService:3, Repository:2, Configuration:2}

**5-line summary**
1. Entry points: 1 gRPC service `ReportingGrpcService` (RequestExport/GetExportJob/ListExportJobs/GetDownloadLocation); scheduled `ExportJobWorker` (opt-out via worker.enabled).
2. Data+render: `ReportDataProvider` pulls REAL livestock-task data over gRPC (per-service token, livestock audience) for LIVESTOCK_TASKS; other types render a placeholder summary. `ReportRenderer` = real CSV (RFC-4180) / XLSX (POI) / PDF (OpenPDF + Unicode TTF for Vietnamese).
3. Persistence/idempotency: `ExportJobEntity` (`rpt_export_job`), idempotent on (tenant_id, idempotency_key); QUEUED→RUNNING→COMPLETED/FAILED. Tenant from verified context; per-method `SCOPE_report:write/read`.
4. RISKs: NO Flyway + no prod ddl-auto override (Hibernate schema in prod); `GetDownloadLocation` returns `file://` local path not presigned object-storage URL; most report types are placeholder summaries; 0 tests.
5. Verdict: PARTIAL_IMPLEMENTATION — genuine job model + multi-format rendering + one real gRPC data source, but prod schema management, artifact storage and most report sources are still starter-grade.
