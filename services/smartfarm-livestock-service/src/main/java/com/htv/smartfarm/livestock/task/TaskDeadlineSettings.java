package com.htv.smartfarm.livestock.task;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deadline windows for the task monitor. When a task is assigned, the accept deadline is
 * {@code assignedAt + acceptWindow} and the report deadline is {@code assignedAt + reportWindow}.
 * Windows can be overridden per {@link com.htv.smartfarm.proto.livestock.v1.TaskType} via the
 * {@code report-window-seconds-by-type} map (key = TaskType enum name), else the default applies.
 * An AssignTask request may override both windows explicitly.
 */
@ConfigurationProperties(prefix = "smartfarm.livestock.deadlines")
public class TaskDeadlineSettings {

    /**
     * Seconds an assignee has to ACCEPT before accept-overdue fires. Default 10 minutes.
     */
    private long acceptWindowSeconds = 600;

    /**
     * Default seconds to report/complete after assignment before report-overdue fires. Default 2 hours.
     */
    private long reportWindowSeconds = 7200;

    /**
     * Per-TaskType report window overrides, key = TaskType enum name (e.g. TASK_TYPE_MEDICATION).
     */
    private Map<String, Long> reportWindowSecondsByType = Map.of();

    public long getAcceptWindowSeconds() {
        return acceptWindowSeconds;
    }

    public void setAcceptWindowSeconds(long v) {
        this.acceptWindowSeconds = v;
    }

    public long getReportWindowSeconds() {
        return reportWindowSeconds;
    }

    public void setReportWindowSeconds(long v) {
        this.reportWindowSeconds = v;
    }

    public Map<String, Long> getReportWindowSecondsByType() {
        return reportWindowSecondsByType;
    }

    public void setReportWindowSecondsByType(Map<String, Long> v) {
        this.reportWindowSecondsByType = v == null ? Map.of() : v;
    }

    /**
     * Resolve the report window for a task type, falling back to the default.
     */
    public long reportWindowFor(String taskType) {
        if (taskType == null) return reportWindowSeconds;
        return reportWindowSecondsByType.getOrDefault(taskType, reportWindowSeconds);
    }
}
