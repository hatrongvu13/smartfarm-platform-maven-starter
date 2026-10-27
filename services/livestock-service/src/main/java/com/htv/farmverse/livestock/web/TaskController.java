package com.htv.farmverse.livestock.web;

import java.util.List;

import com.htv.farmverse.livestock.task.TaskCommandService;
import com.htv.farmverse.livestock.task.TaskEntity;
import com.htv.farmverse.proto.common.v1.RequestContext;
import com.htv.farmverse.proto.livestock.v1.CreateTaskRequest;
import com.htv.farmverse.service.security.FarmVerseClaims;
import com.htv.farmverse.service.web.ApiProblem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Công việc chăm sóc. Tạo task bắt buộc header {@code Idempotency-Key}: gửi lại cùng khóa
 * trả lại task cũ thay vì tạo trùng.
 */
@Validated
@RestController
@RequestMapping("/tasks")
public class TaskController {

    static final String CAN_READ = "hasAuthority('livestock:task:read') or hasRole('SUPER_ADMIN')";
    static final String CAN_WRITE = "hasAuthority('livestock:task:write') or hasRole('SUPER_ADMIN')";

    private final TaskCommandService tasks;

    public TaskController(TaskCommandService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    public List<TaskView> list(@AuthenticationPrincipal Jwt jwt,
                               @RequestParam @NotBlank String farmId,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String assigneeId,
                               @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return tasks.list(tenant(jwt), farmId, status, assigneeId, limit).stream().map(TaskView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_WRITE)
    public TaskView create(@AuthenticationPrincipal Jwt jwt,
                           @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
                           @Valid @RequestBody NewTask body) {
        CreateTaskRequest.Builder request = CreateTaskRequest.newBuilder()
                .setContext(RequestContext.newBuilder().setIdempotencyKey(idempotencyKey).setActorId(jwt.getSubject()))
                .setFarmId(body.farmId())
                .setTitle(body.title());
        if (body.assigneeId() != null) request.setAssigneeId(body.assigneeId());
        String tenant = tenant(jwt);
        var created = tasks.create(tenant, jwt.getSubject(), request.build());
        return TaskView.of(tasks.entityView(tenant, created.id()));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize(CAN_WRITE)
    public TaskView accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return TaskView.of(tasks.accept(tenant(jwt), jwt.getSubject(), id, null));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(CAN_WRITE)
    public TaskView complete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                             @RequestBody(required = false) Note body) {
        return TaskView.of(tasks.complete(tenant(jwt), jwt.getSubject(), id, body == null ? "" : body.text(), null));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_WRITE)
    public TaskView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                           @RequestBody(required = false) Note body) {
        return TaskView.of(tasks.cancel(tenant(jwt), jwt.getSubject(), id, body == null ? "" : body.text(), null));
    }

    @ExceptionHandler(TaskCommandService.NoSuchTaskException.class)
    ApiProblem notFound(TaskCommandService.NoSuchTaskException e) {
        return ApiProblem.notFound("SF-LIVESTOCK-002", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ApiProblem conflict(IllegalStateException e) {
        return ApiProblem.conflict("SF-LIVESTOCK-003", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ApiProblem invalid(IllegalArgumentException e) {
        return ApiProblem.badRequest("SF-LIVESTOCK-004", e.getMessage());
    }

    private static String tenant(Jwt jwt) {
        return jwt.getClaimAsString(FarmVerseClaims.TENANT_ID);
    }

    public record NewTask(@NotBlank @Size(max = 100) String farmId,
                          @NotBlank @Size(max = 200) String title,
                          @Size(max = 100) String assigneeId) {
    }

    public record Note(@Size(max = 1000) String text) {
    }

    public record TaskView(String id, String farmId, String title, String assigneeId, String status,
                           String taskType, long createdAt, Long dueAt) {
        static TaskView of(TaskEntity t) {
            return new TaskView(t.getId(), t.getFarmId(), t.getTitle(), t.getAssigneeId(), t.getStatus(),
                    t.getTaskType(), t.getCreatedAt(), t.getDueAt());
        }
    }
}
