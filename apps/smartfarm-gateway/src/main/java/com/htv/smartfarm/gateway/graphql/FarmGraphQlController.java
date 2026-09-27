package com.htv.smartfarm.gateway.graphql;

import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.proto.common.v1.PageRequest;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.livestock.v1.*;
import com.htv.smartfarm.proto.order.v1.*;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * GraphQL v1 read API — one flexible query surface over the same gRPC services the REST
 * controllers proxy. Lets the frontend fetch exactly the fields it needs (tasks, orders) in a
 * single round-trip. Every resolver derives tenant/actor from the authenticated JWT and calls
 * downstream with a per-service token (Phase 2), identical to the REST path. Dev profile only
 * (reuses the dev gRPC stubs); a prod build would wire prod stubs the same way.
 */
@Controller
@Profile("dev & !prod")
public class FarmGraphQlController {

    private static final String LIVESTOCK_AUDIENCE = "smartfarm-livestock";
    private static final String ORDER_AUDIENCE = "smartfarm-order";

    private final LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub livestock;
    private final FarmOrderServiceGrpc.FarmOrderServiceBlockingStub order;
    private final ServiceTokenClient tokens;

    public FarmGraphQlController(LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub livestockStub,
                                 FarmOrderServiceGrpc.FarmOrderServiceBlockingStub orderStub,
                                 ServiceTokenClient tokens) {
        this.livestock = livestockStub;
        this.order = orderStub;
        this.tokens = tokens;
    }

    private Mono<Jwt> jwt() {
        return ReactiveSecurityContextHolder.getContext().map(c -> (Jwt) c.getAuthentication().getPrincipal());
    }

    private RequestContext ctx(Jwt jwt) {
        return RequestContext.newBuilder()
                .setTenantId(jwt.getClaimAsString("tenant_id")).setActorId(jwt.getSubject()).build();
    }

    private LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub liveStub(Jwt jwt) {
        String t = tokens.tokenFor(LIVESTOCK_AUDIENCE, jwt.getClaimAsString("tenant_id"), jwt.getSubject());
        return livestock.withDeadlineAfter(5, TimeUnit.SECONDS).withCallCredentials(new BearerCallCredentials(() -> t));
    }

    private FarmOrderServiceGrpc.FarmOrderServiceBlockingStub orderStub(Jwt jwt) {
        String t = tokens.tokenFor(ORDER_AUDIENCE, jwt.getClaimAsString("tenant_id"), jwt.getSubject());
        return order.withDeadlineAfter(5, TimeUnit.SECONDS).withCallCredentials(new BearerCallCredentials(() -> t));
    }

    // ---- tasks ----

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_farm:read')")
    public Mono<List<Map<String, Object>>> tasks(@Argument String farmId, @Argument String status,
                                                  @Argument String assigneeId, @Argument Integer limit) {
        return jwt().map(jwt -> {
            var b = ListTasksRequest.newBuilder().setContext(ctx(jwt)).setFarmId(farmId)
                    .setPage(PageRequest.newBuilder().setPageSize(limit == null || limit <= 0 ? 50 : limit));
            if (status != null && !status.isBlank()) {
                try { b.setStatus(TaskStatus.valueOf(status.startsWith("TASK_STATUS_") ? status : "TASK_STATUS_" + status)); }
                catch (IllegalArgumentException ignore) { }
            }
            if (assigneeId != null && !assigneeId.isBlank()) b.setAssigneeId(assigneeId);
            var resp = liveStub(jwt).listTasks(b.build());
            List<Map<String, Object>> outList = new ArrayList<>();
            for (LivestockTask t : resp.getTasksList()) outList.add(taskMap(t));
            return outList;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_farm:read')")
    public Mono<Map<String, Object>> task(@Argument String id) {
        return jwt().map(jwt -> {
            var t = liveStub(jwt).getTask(GetTaskRequest.newBuilder().setContext(ctx(jwt)).setTaskId(id).build()).getTask();
            return taskMap(t);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ---- orders ----

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public Mono<List<Map<String, Object>>> orders(@Argument String farmId, @Argument String status, @Argument Integer limit) {
        return jwt().map(jwt -> {
            var b = ListOrdersRequest.newBuilder().setContext(ctx(jwt)).setFarmId(farmId)
                    .setPage(PageRequest.newBuilder().setPageSize(limit == null || limit <= 0 ? 50 : limit));
            if (status != null && !status.isBlank()) {
                try { b.setStatus(OrderStatus.valueOf(status.startsWith("ORDER_STATUS_") ? status : "ORDER_STATUS_" + status)); }
                catch (IllegalArgumentException ignore) { }
            }
            var resp = orderStub(jwt).listOrders(b.build());
            List<Map<String, Object>> outList = new ArrayList<>();
            for (FarmOrder o : resp.getOrdersList()) outList.add(orderMap(o));
            return outList;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_orders:read')")
    public Mono<Map<String, Object>> order(@Argument String id) {
        return jwt().map(jwt -> {
            var o = orderStub(jwt).getOrder(GetOrderRequest.newBuilder().setContext(ctx(jwt)).setOrderId(id).build()).getOrder();
            return orderMap(o);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ---- mappers ----

    private static Double ms(com.google.protobuf.Timestamp t) {
        return (double) (t.getSeconds() * 1000 + t.getNanos() / 1_000_000);
    }

    private static Map<String, Object> taskMap(LivestockTask t) {
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

    private static Map<String, Object> orderMap(FarmOrder o) {
        var m = new LinkedHashMap<String, Object>();
        m.put("orderId", o.getOrderId());
        m.put("farmId", o.getFarmId());
        if (!o.getBatchId().isBlank()) m.put("batchId", o.getBatchId());
        m.put("status", o.getStatus().name());
        m.put("totalMinor", (double) o.getTotal().getMinorUnits());
        if (!o.getFailureReason().isBlank()) m.put("failureReason", o.getFailureReason());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderLine ln : o.getLinesList()) {
            var lm = new LinkedHashMap<String, Object>();
            lm.put("itemId", ln.getItemId());
            String dec = ln.getQuantity().getDecimalValue();
            lm.put("quantity", dec.isBlank() ? 0d : Double.parseDouble(dec));
            if (!ln.getQuantity().getUnit().isBlank()) lm.put("unit", ln.getQuantity().getUnit());
            lm.put("unitPriceMinor", (double) ln.getUnitPrice().getMinorUnits());
            // warehouse-per-line: rỗng = dùng batchId cấp đơn (tương thích ngược)
            if (!ln.getWarehouseId().isBlank()) lm.put("warehouseId", ln.getWarehouseId());
            lines.add(lm);
        }
        m.put("lines", lines);
        return m;
    }
}
