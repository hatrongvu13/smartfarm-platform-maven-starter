package com.htv.smartfarm.livestock.schedule;

import com.htv.smartfarm.livestock.task.TaskEntity;
import com.htv.smartfarm.livestock.task.TaskStore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materialises concrete tasks from recurring schedules. Each cycle it scans schedules whose
 * {@code nextRunAt} has arrived and, for each, creates ONE task then advances {@code nextRunAt}
 * to the following cron occurrence.
 *
 * <p><b>Idempotency / at-least-once safety.</b> The generated task's idempotency key is
 * {@code sched:<scheduleId>:<firedSlotMs>}. Because {@code sf_task} has a unique constraint on
 * {@code (tenant_id, idempotency_key)}, the same schedule + fire slot can never produce two
 * tasks — even if this runs twice (restart) or on two instances concurrently: the duplicate
 * insert is rejected and skipped. This makes the generator safe WITHOUT a distributed lock; it
 * is still a single-instance DEV component (like {@code OutboxRelay}) — multiple instances would
 * merely do redundant work, not corrupt data. A production deployment should add a claim/lock.
 *
 * <p>Gated on {@code smartfarm.livestock.schedule-generator.enabled} (default true); scheduling
 * itself is enabled by {@link com.htv.smartfarm.livestock.outbox.OutboxScheduling}.
 */
@Component
@ConditionalOnProperty(prefix = "smartfarm.livestock.schedule-generator", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ScheduleTaskGenerator {

    private static final Logger AUDIT = LoggerFactory.getLogger("smartfarm.audit.schedule");

    private final ScheduleJpaRepository schedules;
    private final TaskStore tasks;

    public ScheduleTaskGenerator(ScheduleJpaRepository schedules, TaskStore tasks) {
        this.schedules = schedules;
        this.tasks = tasks;
    }

    @Scheduled(fixedDelayString = "${smartfarm.livestock.schedule-generator.poll-ms:30000}")
    @Transactional
    public void generate() {
        long now = Instant.now().toEpochMilli();
        List<ScheduleEntity> due = schedules.due(now, PageRequest.of(0, 100));
        int created = 0;
        for (ScheduleEntity s : due) {
            long firedSlot = s.getNextRunAt(); // the slot we are firing for
            if (materialise(s, firedSlot)) created++;
            // Advance to the next occurrence AFTER the slot we just fired, so we never re-fire it.
            Long next = ScheduleCommandService.nextRun(s.getCronExpression(), s.getTimeZone(), firedSlot);
            s.setNextRunAt(next);
            schedules.save(s);
        }
        if (created > 0) AUDIT.info("schedule_generated count={} scanned={}", created, due.size());
    }

    /**
     * Create the task for this fire slot; returns false if it already existed (idempotent skip).
     */
    private boolean materialise(ScheduleEntity s, long firedSlot) {
        String key = "sched:" + s.getId() + ":" + firedSlot;
        if (tasks.byIdempotency(s.getTenantId(), key).isPresent()) return false;
        try {
            var task = new TaskEntity(UUID.randomUUID().toString(), s.getTenantId(), s.getFarmId(),
                    s.getTitle(), s.getAssigneeId() == null ? "" : s.getAssigneeId(),
                    "TASK_STATUS_CREATED", s.getTaskType(), firedSlot, firedSlot, key);
            tasks.save(task);
            tasks.insertLifecycleOutbox(s.getTenantId(), task.getId(), "task-created.v1", s.getId(), firedSlot);
            AUDIT.info("schedule_task_created schedule_id={} tenant={} farm_id={} task_id={} slot={}",
                    s.getId(), s.getTenantId(), s.getFarmId(), task.getId(), firedSlot);
            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException raced) {
            // Another cycle/instance won the unique-key race for this slot — safe to skip.
            AUDIT.info("schedule_task_dup_skip schedule_id={} slot={}", s.getId(), firedSlot);
            return false;
        }
    }
}
