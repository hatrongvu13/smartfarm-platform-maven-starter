package com.htv.smartfarm.livestock.schedule;

import com.htv.smartfarm.proto.livestock.v1.CreateScheduleRequest;
import com.htv.smartfarm.proto.livestock.v1.TaskSchedule;
import com.htv.smartfarm.proto.livestock.v1.TaskType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write/read model for recurring task schedules. The cron expression is validated and its
 * {@code nextRunAt} computed in the schedule's own {@link ZoneId} — never the server zone —
 * so DST transitions are handled by {@link CronExpression} rather than assumed away.
 */
@Service
public class ScheduleCommandService {

    private static final Logger AUDIT = LoggerFactory.getLogger("smartfarm.audit.schedule");

    private final ScheduleJpaRepository schedules;

    public ScheduleCommandService(ScheduleJpaRepository schedules) {
        this.schedules = schedules;
    }

    private static String safe(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    /** Validate the cron expression and compute the next fire time after {@code fromEpochMs} in {@code zoneId}. */
    static Long nextRun(String cron, String zone, long fromEpochMs) {
        CronExpression expr;
        try {
            expr = CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid cron_expression: " + e.getMessage());
        }
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(zone);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid time_zone (IANA id required, e.g. Asia/Ho_Chi_Minh)");
        }
        ZonedDateTime from = Instant.ofEpochMilli(fromEpochMs).atZone(zoneId);
        ZonedDateTime next = expr.next(from);
        return next == null ? null : next.toInstant().toEpochMilli();
    }

    @Transactional
    public ScheduleEntity create(String tenant, String actor, CreateScheduleRequest req) {
        if (tenant == null || tenant.isBlank()) throw new SecurityException("authenticated tenant required");
        if (!req.hasSchedule()) throw new IllegalArgumentException("schedule required");
        TaskSchedule s = req.getSchedule();
        if (s.getFarmId().isBlank()) throw new IllegalArgumentException("farm_id required");
        if (s.getTitle().isBlank()) throw new IllegalArgumentException("title required");
        if (s.getCronExpression().isBlank()) throw new IllegalArgumentException("cron_expression required");
        String zone = s.getTimeZone().isBlank() ? "UTC" : s.getTimeZone();
        long now = Instant.now().toEpochMilli();
        Long next = nextRun(s.getCronExpression(), zone, now); // also validates cron + zone

        String taskType = s.getType() == TaskType.TASK_TYPE_UNSPECIFIED ? null : s.getType().name();
        var e = new ScheduleEntity(UUID.randomUUID().toString(), tenant, s.getFarmId(), s.getTitle(), taskType,
                s.getCronExpression(), zone, emptyToNull(s.getAssigneeId()), s.getEnabled(), next, now);
        schedules.save(e);
        AUDIT.info("schedule_created tenant={} actor_id={} schedule_id={} farm_id={} cron='{}' tz={} next_run={}",
                tenant, safe(req.getContext().getActorId()), e.getId(), e.getFarmId(), e.getCronExpression(), zone, next);
        return e;
    }

    @Transactional(readOnly = true)
    public List<ScheduleEntity> list(String tenant, String farm, int limit) {
        if (farm == null || farm.isBlank()) throw new IllegalArgumentException("farm_id required");
        return schedules.list(tenant, farm, PageRequest.of(0, Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200))));
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
