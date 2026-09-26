package com.htv.smartfarm.gateway.livestock;

import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.livestock.v1.*;
import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;
import io.grpc.StatusRuntimeException;

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

@RestController
@Profile("dev & !prod")
@RequestMapping("/api/v1/livestock/tasks")
public class LivestockDevController {
    /** Downstream audience this controller talks to; the gateway mints a service token for it. */
    private static final String LIVESTOCK_AUDIENCE = "smartfarm-livestock";

    private final LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub stub;
    private final ServiceTokenClient serviceTokens;

    public LivestockDevController(LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub stub,
                                  ServiceTokenClient serviceTokens) {
        this.stub = stub;
        this.serviceTokens = serviceTokens;
    }

    public record Create(String farmId, String title, String assigneeId) {
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_tasks:write')")
    public Mono<Map<String, String>> create(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String key, @RequestBody Create body) {
        return Mono.fromCallable(() -> {
            if (body == null || body.farmId() == null || body.title() == null || key.isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = CreateTaskRequest.newBuilder().setContext(RequestContext.newBuilder().setTenantId(tenant)
                            .setActorId(actor).setIdempotencyKey(key).build()).setFarmId(body.farmId()).setTitle(body.title())
                    .setAssigneeId(body.assigneeId() == null ? "" : body.assigneeId()).build();
            try {
                // Per-service token (Hướng A): the gateway calls livestock as itself, NOT with the user's
                // access token. The user identity is carried as actor_id in RequestContext above for audit.
                String serviceToken = serviceTokens.tokenFor(LIVESTOCK_AUDIENCE, tenant, actor);
                var reply = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                        .withCallCredentials(new BearerCallCredentials(() -> serviceToken)).createTask(req);
                return Map.of("taskId", reply.getTaskId(), "status", reply.getStatus());
            } catch (StatusRuntimeException e) {
                var code = e.getStatus().getCode();
                if (code == io.grpc.Status.Code.UNAUTHENTICATED) serviceTokens.invalidate(LIVESTOCK_AUDIENCE);

                org.slf4j.LoggerFactory
                        .getLogger(LivestockDevController.class)
                        .warn("Livestock CreateTask failed: grpcStatus={}, description={}",
                                code, e.getStatus().getDescription());

                HttpStatus httpStatus = switch (code) {
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
                    case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
                    case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
                    case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
                    case UNAVAILABLE, UNIMPLEMENTED -> HttpStatus.BAD_GATEWAY;
                    default -> HttpStatus.BAD_GATEWAY;
                };

                throw new ResponseStatusException(
                        httpStatus,
                        "Livestock CreateTask failed: " + code
                );
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_farm:read')")
    public Mono<Map<String, String>> get(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return Mono.fromCallable(() -> {
            String tenant = jwt.getClaimAsString("tenant_id");
            String actor = jwt.getSubject();
            var req = GetTaskRequest.newBuilder().setContext(RequestContext.newBuilder().setTenantId(tenant).setActorId(actor).build()).setTaskId(id).build();
            try {
                String serviceToken = serviceTokens.tokenFor(LIVESTOCK_AUDIENCE, tenant, actor);
                var task = stub.withDeadlineAfter(3, TimeUnit.SECONDS).withCallCredentials(new BearerCallCredentials(() -> serviceToken)).getTask(req).getTask();
                return Map.of("taskId", task.getTaskId(), "farmId", task.getFarmId(), "title", task.getTitle(), "status", task.getStatus().name());
            } catch (StatusRuntimeException e) {
                var code = e.getStatus().getCode();
                if (code == io.grpc.Status.Code.UNAUTHENTICATED) serviceTokens.invalidate(LIVESTOCK_AUDIENCE);

                org.slf4j.LoggerFactory
                        .getLogger(LivestockDevController.class)
                        .warn("Livestock GetTask failed: grpcStatus={}, description={}",
                                code, e.getStatus().getDescription());

                HttpStatus httpStatus = switch (code) {
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
                    case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
                    case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
                    case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
                    case UNAVAILABLE, UNIMPLEMENTED -> HttpStatus.BAD_GATEWAY;
                    default -> HttpStatus.BAD_GATEWAY;
                };

                throw new ResponseStatusException(
                        httpStatus,
                        "Livestock GetTask failed: " + code
                );
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
