package com.htv.smartfarm.livestock.task;

import com.htv.smartfarm.proto.livestock.v1.CreateTaskRequest;

import java.time.Instant;
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

    public TaskCommandService(TaskStore store) {
        this.store = store;
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
        var task = new TaskStore.Task(UUID.randomUUID().toString(), tenant, request.getFarmId(), request.getTitle(), request.getAssigneeId(), "CREATED", Instant.now().toEpochMilli());
        store.insert(task, key);
        store.insertOutbox(task, request.getContext().getCorrelationId());
        AUDIT.info("task_created tenant={} caller={} actor_id={} correlation_id={} task_id={} farm_id={}",
                tenant, actor, safe(humanActor), safe(correlation), task.id(), task.farm());
        return task;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    @Transactional(readOnly = true)
    public TaskStore.Task get(String tenant, String id) {
        return store.byId(tenant, id).orElse(null);
    }
}
