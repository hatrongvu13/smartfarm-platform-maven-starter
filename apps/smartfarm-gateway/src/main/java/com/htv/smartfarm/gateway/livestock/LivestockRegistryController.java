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
 * DEV REST facade for the livestock animal registry and recurring schedules. Separate from
 * {@link LivestockDevController} (which owns task lifecycle) purely so each has a clean base
 * path; both share the same gRPC stub and per-service-token pattern. {@code @Profile("dev & !prod")}.
 */
@RestController
@RequestMapping("/api/v1/livestock")
public class LivestockRegistryController {

    private static final Logger log = LoggerFactory.getLogger(LivestockRegistryController.class);
    private static final String LIVESTOCK_AUDIENCE = "smartfarm-livestock";

    private final LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub stub;
    private final ServiceTokenClient serviceTokens;

    public LivestockRegistryController(LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub stub,
                                       ServiceTokenClient serviceTokens) {
        this.stub = stub;
        this.serviceTokens = serviceTokens;
    }

    // ---- request bodies ----
    public record RegisterAnimal(String farmId, String tagCode, String species, String barnId, String batchId,
                                 Long birthDateEpochMs) {
    }

    public record CreateSchedule(String farmId, String title, String type, String cronExpression, String timeZone,
                                 String assigneeId, Boolean enabled) {
    }

    // ---- helpers ----

    private LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub authed(String tenant, String actor) {
        String serviceToken = serviceTokens.tokenFor(LIVESTOCK_AUDIENCE, tenant, actor);
        return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                .withCallCredentials(new BearerCallCredentials(() -> serviceToken));
    }

    private RequestContext ctx(String tenant, String actor) {
        return RequestContext.newBuilder().setTenantId(tenant).setActorId(actor).setCorrelationId(actor).build();
    }

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

    // ---- animals ----

    @PostMapping("/animals")
    @PreAuthorize("hasAuthority('SCOPE_tasks:write')")
    public Mono<Map<String, Object>> registerAnimal(@AuthenticationPrincipal Jwt jwt, @RequestBody RegisterAnimal body) {
        return call("RegisterAnimal", () -> {
            if (body == null || body.farmId() == null || body.tagCode() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "farmId and tagCode required");
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var animal = Animal.newBuilder().setFarmId(body.farmId()).setTagCode(body.tagCode());
            if (body.species() != null) animal.setSpecies(body.species());
            if (body.barnId() != null) animal.setBarnId(body.barnId());
            if (body.batchId() != null) animal.setBatchId(body.batchId());
            if (body.birthDateEpochMs() != null)
                animal.setBirthDate(com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(body.birthDateEpochMs() / 1000).setNanos((int) (body.birthDateEpochMs() % 1000) * 1_000_000));
            var req = RegisterAnimalRequest.newBuilder().setContext(ctx(tenant, actor)).setAnimal(animal).build();
            return animalView(authed(tenant, actor).registerAnimal(req).getAnimal());
        });
    }

    @GetMapping("/animals/{id}")
    @PreAuthorize("hasAuthority('SCOPE_farm:read')")
    public Mono<Map<String, Object>> getAnimal(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return call("GetAnimal", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = GetAnimalRequest.newBuilder().setContext(ctx(tenant, actor)).setAnimalId(id).build();
            return animalView(authed(tenant, actor).getAnimal(req).getAnimal());
        });
    }

    @GetMapping("/animals")
    @PreAuthorize("hasAuthority('SCOPE_farm:read')")
    public Mono<Map<String, Object>> listAnimals(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam String farmId,
                                                  @RequestParam(required = false) String batchId,
                                                  @RequestParam(required = false, defaultValue = "50") int limit) {
        return call("ListAnimals", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var b = ListAnimalsRequest.newBuilder().setContext(ctx(tenant, actor)).setFarmId(farmId)
                    .setPage(PageRequest.newBuilder().setPageSize(limit));
            if (batchId != null && !batchId.isBlank()) b.setBatchId(batchId);
            var resp = authed(tenant, actor).listAnimals(b.build());
            List<Map<String, Object>> items = new ArrayList<>();
            for (Animal a : resp.getAnimalsList()) items.add(animalView(a));
            return Map.of("count", items.size(), "animals", items);
        });
    }

    // ---- schedules ----

    @PostMapping("/schedules")
    @PreAuthorize("hasAuthority('SCOPE_tasks:write')")
    public Mono<Map<String, Object>> createSchedule(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateSchedule body) {
        return call("CreateSchedule", () -> {
            if (body == null || body.farmId() == null || body.title() == null || body.cronExpression() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "farmId, title, cronExpression required");
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var sched = TaskSchedule.newBuilder().setFarmId(body.farmId()).setTitle(body.title())
                    .setType(type(body.type())).setCronExpression(body.cronExpression())
                    .setTimeZone(body.timeZone() == null ? "" : body.timeZone())
                    .setEnabled(body.enabled() == null || body.enabled());
            if (body.assigneeId() != null) sched.setAssigneeId(body.assigneeId());
            var req = CreateScheduleRequest.newBuilder().setContext(ctx(tenant, actor)).setSchedule(sched).build();
            return scheduleView(authed(tenant, actor).createSchedule(req).getSchedule());
        });
    }

    @GetMapping("/schedules")
    @PreAuthorize("hasAuthority('SCOPE_farm:read')")
    public Mono<Map<String, Object>> listSchedules(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestParam String farmId,
                                                    @RequestParam(required = false, defaultValue = "50") int limit) {
        return call("ListSchedules", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = ListSchedulesRequest.newBuilder().setContext(ctx(tenant, actor)).setFarmId(farmId)
                    .setPage(PageRequest.newBuilder().setPageSize(limit)).build();
            var resp = authed(tenant, actor).listSchedules(req);
            List<Map<String, Object>> items = new ArrayList<>();
            for (TaskSchedule s : resp.getSchedulesList()) items.add(scheduleView(s));
            return Map.of("count", items.size(), "schedules", items);
        });
    }

    // ---- views ----

    private static Map<String, Object> animalView(Animal a) {
        var m = new LinkedHashMap<String, Object>();
        m.put("animalId", a.getAnimalId());
        m.put("farmId", a.getFarmId());
        m.put("tagCode", a.getTagCode());
        m.put("status", a.getStatus().name());
        if (!a.getSpecies().isBlank()) m.put("species", a.getSpecies());
        if (!a.getBarnId().isBlank()) m.put("barnId", a.getBarnId());
        if (!a.getBatchId().isBlank()) m.put("batchId", a.getBatchId());
        if (a.hasBirthDate()) m.put("birthDate", ms(a.getBirthDate()));
        return m;
    }

    private static Map<String, Object> scheduleView(TaskSchedule s) {
        var m = new LinkedHashMap<String, Object>();
        m.put("scheduleId", s.getScheduleId());
        m.put("farmId", s.getFarmId());
        m.put("title", s.getTitle());
        m.put("type", s.getType().name());
        m.put("cronExpression", s.getCronExpression());
        m.put("timeZone", s.getTimeZone());
        m.put("enabled", s.getEnabled());
        if (!s.getAssigneeId().isBlank()) m.put("assigneeId", s.getAssigneeId());
        if (s.hasNextRunAt()) m.put("nextRunAt", ms(s.getNextRunAt()));
        return m;
    }
}
