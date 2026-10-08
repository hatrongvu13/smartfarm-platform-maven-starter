package com.htv.smartfarm.gateway.livestock;

import com.htv.smartfarm.proto.common.v1.PageRequest;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.livestock.v1.*;
import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;
import com.htv.smartfarm.security.grpc.GrpcStatusHttpMapping;
import io.grpc.StatusRuntimeException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * DEV REST facade over the livestock gRPC service. Proxies the full task lifecycle
 * (create / assign / accept / complete / cancel / list / get). Every call is made with a
 * per-service token minted for the livestock audience; the human user is carried as
 * {@code actor_id} in RequestContext for audit. {@code @Profile("dev & !prod")} keeps this
 * off in production — clients still reach these features through the gateway only.
 */
@RestController
@RequestMapping("/api/v1/livestock/tasks")
public class LivestockDevController {

    private static final Logger log = LoggerFactory.getLogger(LivestockDevController.class);
    private static final String LIVESTOCK_AUDIENCE = "smartfarm-livestock";

    private final LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub stub;
    private final ServiceTokenClient serviceTokens;

    public LivestockDevController(LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub stub,
                                  ServiceTokenClient serviceTokens) {
        this.stub = stub;
        this.serviceTokens = serviceTokens;
    }

    // ---- request bodies ----
    public record Create(String farmId, String title, String assigneeId, String type, Long dueAtEpochMs) {
    }

    public record Assign(String assigneeId, Long acceptWindowSeconds, Long reportWindowSeconds) {
    }

    public record Cancel(String reason) {
    }

    public record Complete(String note) {
    }

    // ---- helpers ----

    private LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub authed(String tenant, String actor) {
        String serviceToken = serviceTokens.tokenFor(LIVESTOCK_AUDIENCE, tenant, actor);
        return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                .withCallCredentials(new BearerCallCredentials(() -> serviceToken));
    }

    private RequestContext ctx(String tenant, String actor, String key) {
        var b = RequestContext.newBuilder().setTenantId(tenant).setActorId(actor).setCorrelationId(actor);
        if (key != null && !key.isBlank()) b.setIdempotencyKey(key);
        return b.build();
    }

    /** Run a gRPC call on the elastic scheduler, mapping gRPC status to HTTP on failure. */
    private <T> Mono<T> call(String op, java.util.concurrent.Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(StatusRuntimeException.class, e -> {
                    var code = e.getStatus().getCode();
                    if (code == io.grpc.Status.Code.UNAUTHENTICATED) serviceTokens.invalidate(LIVESTOCK_AUDIENCE);
                    log.warn("Livestock {} failed: grpcStatus={}, description={}", op, code, e.getStatus().getDescription());
                    HttpStatus http = GrpcStatusHttpMapping.httpStatus(code);
                    return new ResponseStatusException(http, "Livestock " + op + " failed: " + code
                            + (e.getStatus().getDescription() == null ? "" : " (" + e.getStatus().getDescription() + ")"));
                });
    }

    /** Serialise a LivestockTask (including monitoring timestamps) to a JSON-friendly map. */
    private static Map<String, Object> view(LivestockTask t) {
        var m = new LinkedHashMap<String, Object>();
        m.put("taskId", t.getTaskId());
        m.put("farmId", t.getFarmId());
        m.put("title", t.getTitle());
        m.put("type", t.getType().name());
        m.put("status", t.getStatus().name());
        m.put("assigneeId", t.getAssigneeId());
        if (t.hasDueAt()) m.put("dueAt", ms(t.getDueAt()));
        if (t.hasAssignedAt()) m.put("assignedAt", ms(t.getAssignedAt()));
        if (t.hasAcceptDeadlineAt()) m.put("acceptDeadlineAt", ms(t.getAcceptDeadlineAt()));
        if (t.hasAcceptedAt()) m.put("acceptedAt", ms(t.getAcceptedAt()));
        if (t.hasReportDueAt()) m.put("reportDueAt", ms(t.getReportDueAt()));
        if (t.hasReportedAt()) m.put("reportedAt", ms(t.getReportedAt()));
        if (t.hasCompletedAt()) m.put("completedAt", ms(t.getCompletedAt()));
        return m;
    }

    private static long ms(com.google.protobuf.Timestamp ts) {
        return ts.getSeconds() * 1000 + ts.getNanos() / 1_000_000;
    }

    private static TaskType type(String name) {
        if (name == null || name.isBlank()) return TaskType.TASK_TYPE_UNSPECIFIED;
        try {
            return TaskType.valueOf(name.startsWith("TASK_TYPE_") ? name : "TASK_TYPE_" + name);
        } catch (IllegalArgumentException e) {
            return TaskType.TASK_TYPE_UNSPECIFIED;
        }
    }

    // ---- endpoints ----

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_tasks:write')")
    public Mono<Map<String, String>> create(@AuthenticationPrincipal Jwt jwt,
                                             @RequestHeader("Idempotency-Key") String key,
                                             @RequestBody Create body) {
        return call("CreateTask", () -> {
            if (body == null || body.farmId() == null || body.title() == null || key.isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = CreateTaskRequest.newBuilder().setContext(ctx(tenant, actor, key))
                    .setFarmId(body.farmId()).setTitle(body.title())
                    .setAssigneeId(body.assigneeId() == null ? "" : body.assigneeId())
                    .setType(type(body.type()));
            if (body.dueAtEpochMs() != null)
                req.setDueAt(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(body.dueAtEpochMs() / 1000).setNanos((int) (body.dueAtEpochMs() % 1000) * 1_000_000));
            var reply = authed(tenant, actor).createTask(req.build());
            return Map.of("taskId", reply.getTaskId(), "status", reply.getStatus());
        });
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('SCOPE_tasks:write')")
    public Mono<Map<String, Object>> assign(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestBody Assign body) {
        return call("AssignTask", () -> {
            if (body == null || body.assigneeId() == null || body.assigneeId().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assigneeId required");
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var b = AssignTaskRequest.newBuilder().setContext(ctx(tenant, actor, null))
                    .setTaskId(id).setAssigneeId(body.assigneeId());
            if (body.acceptWindowSeconds() != null) b.setAcceptWindowSeconds(body.acceptWindowSeconds());
            if (body.reportWindowSeconds() != null) b.setReportWindowSeconds(body.reportWindowSeconds());
            return view(authed(tenant, actor).assignTask(b.build()).getTask());
        });
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('SCOPE_tasks:write')")
    public Mono<Map<String, Object>> accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return call("AcceptTask", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = AcceptTaskRequest.newBuilder().setContext(ctx(tenant, actor, null)).setTaskId(id).build();
            return view(authed(tenant, actor).acceptTask(req).getTask());
        });
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('SCOPE_tasks:write')")
    public Mono<Map<String, Object>> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestBody(required = false) Complete body) {
        return call("CompleteTask", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = CompleteTaskRequest.newBuilder().setContext(ctx(tenant, actor, null)).setTaskId(id)
                    .setCompletionNote(body == null || body.note() == null ? "" : body.note()).build();
            return view(authed(tenant, actor).completeTask(req).getTask());
        });
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_tasks:write')")
    public Mono<Map<String, Object>> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestBody(required = false) Cancel body) {
        return call("CancelTask", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = CancelTaskRequest.newBuilder().setContext(ctx(tenant, actor, null)).setTaskId(id)
                    .setReason(body == null || body.reason() == null ? "" : body.reason()).build();
            return view(authed(tenant, actor).cancelTask(req).getTask());
        });
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_farm:read')")
    public Mono<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam String farmId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String assigneeId,
                                           @RequestParam(required = false, defaultValue = "50") int limit) {
        return call("ListTasks", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var b = ListTasksRequest.newBuilder().setContext(ctx(tenant, actor, null)).setFarmId(farmId)
                    .setPage(PageRequest.newBuilder().setPageSize(limit));
            if (status != null && !status.isBlank()) {
                try {
                    b.setStatus(TaskStatus.valueOf(status.startsWith("TASK_STATUS_") ? status : "TASK_STATUS_" + status));
                } catch (IllegalArgumentException ignore) {
                    // unknown status -> no filter
                }
            }
            if (assigneeId != null && !assigneeId.isBlank()) b.setAssigneeId(assigneeId);
            var resp = authed(tenant, actor).listTasks(b.build());
            List<Map<String, Object>> items = new ArrayList<>();
            for (LivestockTask t : resp.getTasksList()) items.add(view(t));
            return Map.of("count", items.size(), "tasks", items);
        });
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_farm:read')")
    public Mono<Map<String, Object>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return call("GetTask", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = GetTaskRequest.newBuilder().setContext(ctx(tenant, actor, null)).setTaskId(id).build();
            return view(authed(tenant, actor).getTask(req).getTask());
        });
    }
}
