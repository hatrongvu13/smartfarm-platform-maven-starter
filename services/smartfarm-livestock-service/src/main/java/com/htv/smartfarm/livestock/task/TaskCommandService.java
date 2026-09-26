package com.htv.smartfarm.livestock.task;

import com.htv.smartfarm.proto.livestock.v1.CreateTaskRequest;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCommandService {
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
        var existing = store.byIdempotency(tenant, key);
        if (existing.isPresent()) {
            var task = existing.get();
            if (!task.title().equals(request.getTitle()) || !task.farm().equals(request.getFarmId()) || !task.assignee().equals(request.getAssigneeId()))
                throw new IllegalStateException("idempotency key reused with different request");
            return task;
        }
        var task = new TaskStore.Task(UUID.randomUUID().toString(), tenant, request.getFarmId(), request.getTitle(), request.getAssigneeId(), "CREATED", Instant.now().toEpochMilli());
        store.insert(task, key);
        store.insertOutbox(task, request.getContext().getCorrelationId());
        return task;
    }

    @Transactional(readOnly = true)
    public TaskStore.Task get(String tenant, String id) {
        return store.byId(tenant, id).orElse(null);
    }
}
