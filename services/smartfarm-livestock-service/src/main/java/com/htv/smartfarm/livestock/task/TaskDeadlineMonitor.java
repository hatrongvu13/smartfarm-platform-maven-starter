package com.htv.smartfarm.livestock.task;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically scans for tasks that have crossed a monitored deadline and emits the
 * corresponding overdue integration event through the outbox (relayed to MQTT):
 * <ul>
 *   <li>ASSIGNED past {@code acceptDeadlineAt}, not accepted -> {@code task-accept-overdue.v1}</li>
 *   <li>ACCEPTED past {@code reportDueAt}, not reported -> {@code task-report-overdue.v1}</li>
 * </ul>
 * Each task is marked notified once so the event fires exactly once per breach, not every cycle.
 * Gated on {@code smartfarm.livestock.monitor.enabled} (default true); scheduling itself is enabled
 * by {@link com.htv.smartfarm.livestock.outbox.OutboxScheduling}.
 */
@Component
@ConditionalOnProperty(prefix = "smartfarm.livestock.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TaskDeadlineMonitor {

    private static final Logger AUDIT = LoggerFactory.getLogger("smartfarm.audit.task");

    private final TaskStore store;

    public TaskDeadlineMonitor(TaskStore store) {
        this.store = store;
    }

    @Scheduled(fixedDelayString = "${smartfarm.livestock.monitor.poll-ms:30000}")
    @Transactional
    public void scan() {
        long now = Instant.now().toEpochMilli();
        int accept = emitAcceptOverdue(now);
        int report = emitReportOverdue(now);
        if (accept > 0 || report > 0)
            AUDIT.info("task_deadline_scan accept_overdue={} report_overdue={}", accept, report);
    }

    private int emitAcceptOverdue(long now) {
        List<TaskEntity> due = store.acceptOverdue(now, 200);
        for (TaskEntity t : due) {
            store.insertLifecycleOutbox(t.getTenantId(), t.getId(), "task-accept-overdue.v1", t.getId(), now);
            t.markAcceptOverdueNotified(now);
            store.save(t);
            AUDIT.warn("task_accept_overdue tenant={} task_id={} assignee={} accept_deadline={}",
                    t.getTenantId(), t.getId(), t.getAssigneeId(), t.getAcceptDeadlineAt());
        }
        return due.size();
    }

    private int emitReportOverdue(long now) {
        List<TaskEntity> due = store.reportOverdue(now, 200);
        for (TaskEntity t : due) {
            store.insertLifecycleOutbox(t.getTenantId(), t.getId(), "task-report-overdue.v1", t.getId(), now);
            t.markReportOverdueNotified(now);
            store.save(t);
            AUDIT.warn("task_report_overdue tenant={} task_id={} assignee={} report_due={}",
                    t.getTenantId(), t.getId(), t.getAssigneeId(), t.getReportDueAt());
        }
        return due.size();
    }
}
