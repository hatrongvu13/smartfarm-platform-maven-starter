package com.htv.smartfarm.gateway.health;

import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.proto.common.v1.PageRequest;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.health.v1.*;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;
import com.htv.smartfarm.security.grpc.GrpcStatusHttpMapping;
import io.grpc.StatusRuntimeException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
 * GW-01 — DEV REST facade over the health gRPC service (AnimalHealthService): list observations,
 * vaccinations and alerts. Per-service token for the health audience; human actor carried as
 * actor_id. Read-only; write RPCs stay gRPC-internal. {@code @Profile("dev & !prod")}, mirroring
 * {@code ReportingDevController} / {@code LivestockDevController}.
 */
@RestController
@Profile("dev & !prod")
@RequestMapping("/api/v1/health")
public class HealthDevController {

    private static final String HEALTH_AUDIENCE = "smartfarm-health";

    private final AnimalHealthServiceGrpc.AnimalHealthServiceBlockingStub stub;
    private final ServiceTokenClient serviceTokens;

    public HealthDevController(AnimalHealthServiceGrpc.AnimalHealthServiceBlockingStub healthStub,
                               ServiceTokenClient serviceTokens) {
        this.stub = healthStub;
        this.serviceTokens = serviceTokens;
    }

    private AnimalHealthServiceGrpc.AnimalHealthServiceBlockingStub authed(String tenant, String actor) {
        String token = serviceTokens.tokenFor(HEALTH_AUDIENCE, tenant, actor);
        return stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                .withCallCredentials(new BearerCallCredentials(() -> token));
    }

    private RequestContext ctx(String tenant, String actor) {
        return RequestContext.newBuilder()
                .setTenantId(tenant).setActorId(actor).setCorrelationId(actor).build();
    }

    private <T> Mono<T> call(String op, java.util.concurrent.Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(StatusRuntimeException.class, e -> {
                    var code = e.getStatus().getCode();
                    if (code == io.grpc.Status.Code.UNAUTHENTICATED) serviceTokens.invalidate(HEALTH_AUDIENCE);
                    HttpStatus http = GrpcStatusHttpMapping.httpStatus(code);
                    return new ResponseStatusException(http, "Health " + op + " failed: " + code
                            + (e.getStatus().getDescription() == null ? "" : " (" + e.getStatus().getDescription() + ")"));
                });
    }

    private static long ms(com.google.protobuf.Timestamp t) {
        return t.getSeconds() * 1000 + t.getNanos() / 1_000_000;
    }

    private static Map<String, Object> observationView(HealthObservation o) {
        var m = new LinkedHashMap<String, Object>();
        m.put("observationId", o.getObservationId());
        m.put("animalId", o.getAnimalId());
        m.put("farmId", o.getFarmId());
        if (o.hasObservedAt()) m.put("observedAt", ms(o.getObservedAt()));
        m.put("symptom", o.getSymptom());
        m.put("observerId", o.getObserverId());
        if (!o.getNotes().isBlank()) m.put("notes", o.getNotes());
        return m;
    }

    private static Map<String, Object> vaccinationView(Vaccination v) {
        var m = new LinkedHashMap<String, Object>();
        m.put("vaccinationId", v.getVaccinationId());
        m.put("animalId", v.getAnimalId());
        m.put("vaccineName", v.getVaccineName());
        m.put("lotCode", v.getLotCode());
        if (v.hasAdministeredAt()) m.put("administeredAt", ms(v.getAdministeredAt()));
        if (v.hasNextDueAt()) m.put("nextDueAt", ms(v.getNextDueAt()));
        m.put("administratorId", v.getAdministratorId());
        return m;
    }

    private static Map<String, Object> alertView(HealthAlert a) {
        var m = new LinkedHashMap<String, Object>();
        m.put("alertId", a.getAlertId());
        m.put("farmId", a.getFarmId());
        m.put("animalId", a.getAnimalId());
        m.put("deviceId", a.getDeviceId());
        m.put("severity", a.getSeverity().name());
        m.put("status", a.getStatus().name());
        m.put("reason", a.getReason());
        if (a.hasDetectedAt()) m.put("detectedAt", ms(a.getDetectedAt()));
        if (!a.getAcknowledgedBy().isBlank()) m.put("acknowledgedBy", a.getAcknowledgedBy());
        return m;
    }

    @GetMapping("/observations")
    @PreAuthorize("hasAuthority('SCOPE_health:read')")
    public Mono<Map<String, Object>> observations(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam String animalId,
                                                   @RequestParam(required = false, defaultValue = "50") int limit) {
        return call("ListObservations", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = ListObservationsRequest.newBuilder().setContext(ctx(tenant, actor))
                    .setAnimalId(animalId).setPage(PageRequest.newBuilder().setPageSize(limit)).build();
            var resp = authed(tenant, actor).listObservations(req);
            List<Map<String, Object>> items = new ArrayList<>();
            for (HealthObservation o : resp.getObservationsList()) items.add(observationView(o));
            return Map.of("count", items.size(), "observations", items);
        });
    }

    @GetMapping("/vaccinations")
    @PreAuthorize("hasAuthority('SCOPE_health:read')")
    public Mono<Map<String, Object>> vaccinations(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam String animalId,
                                                   @RequestParam(required = false, defaultValue = "50") int limit) {
        return call("ListVaccinations", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = ListVaccinationsRequest.newBuilder().setContext(ctx(tenant, actor))
                    .setAnimalId(animalId).setPage(PageRequest.newBuilder().setPageSize(limit)).build();
            var resp = authed(tenant, actor).listVaccinations(req);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Vaccination v : resp.getVaccinationsList()) items.add(vaccinationView(v));
            return Map.of("count", items.size(), "vaccinations", items);
        });
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('SCOPE_health:read')")
    public Mono<Map<String, Object>> alerts(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam String farmId,
                                            @RequestParam(required = false, defaultValue = "50") int limit) {
        return call("ListAlerts", () -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = ListAlertsRequest.newBuilder().setContext(ctx(tenant, actor))
                    .setFarmId(farmId).setPage(PageRequest.newBuilder().setPageSize(limit)).build();
            var resp = authed(tenant, actor).listAlerts(req);
            List<Map<String, Object>> items = new ArrayList<>();
            for (HealthAlert a : resp.getAlertsList()) items.add(alertView(a));
            return Map.of("count", items.size(), "alerts", items);
        });
    }
}
