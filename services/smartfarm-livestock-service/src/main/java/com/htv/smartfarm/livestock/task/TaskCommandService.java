package com.htv.smartfarm.livestock.task;

import com.htv.smartfarm.proto.livestock.v1.AssignTaskRequest;
import com.htv.smartfarm.proto.livestock.v1.CreateTaskRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCommandService {
    /** Audit channel: records which human actor (from RequestContext.actor_id) triggered each command. */
    private static final Logger AUDIT = LoggerFactory.getLogger("smartfarm.audit.task");

    private final TaskStore store;
    private final TaskDeadlineSettings deadlines;

    public TaskCommandService(TaskStore store, TaskDeadlineSettings deadlines) {
        this.store = store;
        this.deadlines = deadlines;
    }

    @Transactional
    public TaskStore.Task create(String tenant, String actor, CreateTaskRequest request) {
        if (tenant == null || tenant.isBlank() || actor == null || actor.isBlank())
            throw new IllegalArgumentException("authenticated identity required");
        if (!request.hasContext() || request.getContext().getIdempotencyKey().isBlank())
            throw new IllegalArgumentException("idempotency_key required");
        String key = request.getContext().getIdempotencyKey();
        if (key.length() > 128) throw new IllegalArgumentException("idempotency_key too long");
        if (!request.getContext().getTenantId().isBlank() && !tenant.equals(request.getContext().getTenantId()))
            throw new SecurityException("tenant mismatch");
        if (request.getTitle().isBlank() || request.getTitle().length() > 200 || request.getFarmId().isBlank())
            throw new IllegalArgumentException("title and farm_id required");
        // Traceability: `actor` is the authenticated gRPC caller (svc:gateway); `actor_id` in the
        // RequestContext is the human user the gateway acted on behalf of. Log both so any task can be
        // traced back to the person who created it, even though the transport identity is a service.
        String humanActor = request.getContext().getActorId();
        String correlation = request.getContext().getCorrelationId();
        var existing = store.byIdempotency(tenant, key);
        if (existing.isPresent()) {
            var task = existing.get();
            if (!task.title().equals(request.getTitle()) || !task.farm().equals(request.getFarmId()) || !task.assignee().equals(request.getAssigneeId()))
                throw new IllegalStateException("idempotency key reused with different request");
            AUDIT.info("task_create_idempotent_hit tenant={} caller={} actor_id={} correlation_id={} task_id={}",
                    tenant, actor, safe(humanActor), safe(correlation), task.id());
            return task;
        }
        long nowMs = Instant.now().toEpochMilli();
        String taskType = request.getType() == com.htv.smartfarm.proto.livestock.v1.TaskType.TASK_TYPE_UNSPECIFIED
                ? null : request.getType().name();
        Long dueAt = request.hasDueAt() ? request.getDueAt().getSeconds() * 1000 : null;
        var entity = new TaskEntity(UUID.randomUUID().toString(), tenant, request.getFarmId(), request.getTitle(),
                request.getAssigneeId(), "TASK_STATUS_CREATED", taskType, nowMs, dueAt, key);
        store.save(entity);
        store.insertLifecycleOutbox(tenant, entity.getId(), "task-created.v1", correlation, nowMs);
        AUDIT.info("task_created tenant={} caller={} actor_id={} correlation_id={} task_id={} farm_id={} type={}",
                tenant, actor, safe(humanActor), safe(correlation), entity.getId(), entity.getFarmId(), safe(taskType));
        return new TaskStore.Task(entity.getId(), tenant, entity.getFarmId(), entity.getTitle(),
                entity.getAssigneeId(), entity.getStatus(), nowMs);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    @Transactional(readOnly = true)
    public TaskStore.Task get(String tenant, String id) {
        return store.byId(tenant, id).orElse(null);
    }

    /** Full entity view for GetTask (status + lifecycle timestamps). Null when not found. */
    @Transactional(readOnly = true)
    public TaskEntity entityView(String tenant, String id) {
        return store.entity(tenant, id).orElse(null);
    }

    // ---- lifecycle commands -------------------------------------------------

    private TaskEntity require(String tenant, String id) {
        return store.entity(tenant, id).orElseThrow(() -> new NoSuchTaskException(id));
    }

    /** Assign (or re-assign) a task, arming the accept/report deadlines the monitor watches. */
    @Transactional
    public TaskEntity assign(String tenant, String actor, AssignTaskRequest req) {
        String id = req.getTaskId();
        if (id.isBlank() || req.getAssigneeId().isBlank())
            throw new IllegalArgumentException("task_id and assignee_id required");
        var t = require(tenant, id);
        // Assignable from CREATED, ASSIGNED (re-assign) or ACCEPTED (re-assign); terminal states cannot.
        String s = t.getStatus();
        if ("TASK_STATUS_COMPLETED".equals(s) || "TASK_STATUS_CANCELLED".equals(s) || "COMPLETED".equals(s) || "CANCELLED".equals(s))
            throw new IllegalStateException("cannot assign a terminal task");
        long now = Instant.now().toEpochMilli();
        long acceptWindow = req.getAcceptWindowSeconds() > 0 ? req.getAcceptWindowSeconds() : deadlines.getAcceptWindowSeconds();
        long reportWindow = req.getReportWindowSeconds() > 0 ? req.getReportWindowSeconds() : deadlines.reportWindowFor(t.getTaskType());
        t.setAssignment(req.getAssigneeId(), now, now + acceptWindow * 1000, now + reportWindow * 1000);
        store.save(t);
        store.insertLifecycleOutbox(tenant, id, "task-assigned.v1", req.getContext().getCorrelationId(), now);
        AUDIT.info("task_assigned tenant={} actor_id={} task_id={} assignee={} accept_deadline={} report_due={}",
                tenant, safe(req.getContext().getActorId()), id, req.getAssigneeId(), t.getAcceptDeadlineAt(), t.getReportDueAt());
        return t;
    }

    /** Worker accepts an assigned task, stopping the accept-overdue clock. */
    @Transactional
    public TaskEntity accept(String tenant, String actor, String id, String correlation) {
        if (id.isBlank()) throw new IllegalArgumentException("task_id required");
        var t = require(tenant, id);
        if ("TASK_STATUS_ACCEPTED".equals(t.getStatus())) return t; // idempotent
        if (!"TASK_STATUS_ASSIGNED".equals(t.getStatus()))
            throw new IllegalStateException("only an ASSIGNED task can be accepted");
        long now = Instant.now().toEpochMilli();
        t.markAccepted(now);
        store.save(t);
        store.insertLifecycleOutbox(tenant, id, "task-accepted.v1", correlation, now);
        AUDIT.info("task_accepted tenant={} actor_id={} task_id={}", tenant, safe(actor), id);
        return t;
    }

    /** Complete (report) a task, satisfying the report deadline. */
    @Transactional
    public TaskEntity complete(String tenant, String actor, String id, String note, String correlation) {
        if (id.isBlank()) throw new IllegalArgumentException("task_id required");
        var t = require(tenant, id);
        if ("TASK_STATUS_COMPLETED".equals(t.getStatus())) return t; // idempotent
        String s = t.getStatus();
        if ("TASK_STATUS_CANCELLED".equals(s) || "CANCELLED".equals(s))
            throw new IllegalStateException("cannot complete a cancelled task");
        long now = Instant.now().toEpochMilli();
        t.markCompleted(now);
        store.save(t);
        store.insertLifecycleOutbox(tenant, id, "task-completed.v1", correlation, now);
        AUDIT.info("task_completed tenant={} actor_id={} task_id={}", tenant, safe(actor), id);
        return t;
    }

    /** Cancel a task. Idempotent; terminal COMPLETED cannot be cancelled. */
    @Transactional
    public TaskEntity cancel(String tenant, String actor, String id, String reason, String correlation) {
        if (id.isBlank()) throw new IllegalArgumentException("task_id required");
        var t = require(tenant, id);
        if ("TASK_STATUS_CANCELLED".equals(t.getStatus())) return t; // idempotent
        if ("TASK_STATUS_COMPLETED".equals(t.getStatus()) || "COMPLETED".equals(t.getStatus()))
            throw new IllegalStateException("cannot cancel a completed task");
        long now = Instant.now().toEpochMilli();
        t.markCancelled(reason == null ? "" : reason, now);
        store.save(t);
        store.insertLifecycleOutbox(tenant, id, "task-cancelled.v1", correlation, now);
        AUDIT.info("task_cancelled tenant={} actor_id={} task_id={} reason={}", tenant, safe(actor), id, safe(reason));
        return t;
    }

    @Transactional(readOnly = true)
    public List<TaskEntity> list(String tenant, String farm, String status, String assignee, int limit) {
        if (farm == null || farm.isBlank()) throw new IllegalArgumentException("farm_id required");
        return store.list(tenant, farm, emptyToNull(status), emptyToNull(assignee), limit <= 0 ? 50 : limit);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Thrown when a task id is not found in the tenant; mapped to NOT_FOUND at the gRPC edge. */
    public static class NoSuchTaskException extends RuntimeException {
        public NoSuchTaskException(String id) {
            super("task not found: " + id);
        }
    }
}
