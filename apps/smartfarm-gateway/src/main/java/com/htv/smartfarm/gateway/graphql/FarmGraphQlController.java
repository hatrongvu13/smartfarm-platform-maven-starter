package com.htv.smartfarm.gateway.graphql;

import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.proto.common.v1.PageRequest;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.livestock.v1.*;
import com.htv.smartfarm.proto.order.v1.*;
import com.htv.smartfarm.proto.inventory.v1.*;
import com.htv.smartfarm.proto.finance.v1.*;
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
    private static final String INVENTORY_AUDIENCE = "smartfarm-inventory";
    private static final String FINANCE_AUDIENCE = "smartfarm-finance";

    private final LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub livestock;
    private final FarmOrderServiceGrpc.FarmOrderServiceBlockingStub order;
    private final InventoryServiceGrpc.InventoryServiceBlockingStub inventory;
    private final FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub finance;
    private final ServiceTokenClient tokens;
    private final org.springframework.web.reactive.function.client.WebClient identity;

    public FarmGraphQlController(LivestockTaskServiceGrpc.LivestockTaskServiceBlockingStub livestockStub,
                                 FarmOrderServiceGrpc.FarmOrderServiceBlockingStub orderStub,
                                 InventoryServiceGrpc.InventoryServiceBlockingStub gwInventoryStub,
                                 FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub gwFinanceStub,
                                 ServiceTokenClient tokens,
                                 org.springframework.web.reactive.function.client.WebClient identityWebClient) {
        this.livestock = livestockStub;
        this.order = orderStub;
        this.inventory = gwInventoryStub;
        this.finance = gwFinanceStub;
        this.tokens = tokens;
        this.identity = identityWebClient;
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

    private InventoryServiceGrpc.InventoryServiceBlockingStub invStub(Jwt jwt) {
        String t = tokens.tokenFor(INVENTORY_AUDIENCE, jwt.getClaimAsString("tenant_id"), jwt.getSubject());
        return inventory.withDeadlineAfter(5, TimeUnit.SECONDS).withCallCredentials(new BearerCallCredentials(() -> t));
    }

    private FarmFinanceServiceGrpc.FarmFinanceServiceBlockingStub finStub(Jwt jwt) {
        String t = tokens.tokenFor(FINANCE_AUDIENCE, jwt.getClaimAsString("tenant_id"), jwt.getSubject());
        return finance.withDeadlineAfter(5, TimeUnit.SECONDS).withCallCredentials(new BearerCallCredentials(() -> t));
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

    // ---- dashboard (aggregate) ----

    /**
     * One-round-trip Dashboard aggregate: pulls tasks + orders for the farm via the SAME gRPC
     * calls the list resolvers use, then computes status counts and overdue flags server-side.
     * Read-convenience only — no new gRPC/proto, same per-service token + scopes as REST.
     * Needs both farm:read (tasks) and orders:read (orders).
     */
    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_farm:read') and hasAuthority('SCOPE_orders:read')")
    public Mono<Map<String, Object>> dashboard(@Argument String farmId, @Argument Integer recentLimit) {
        return jwt().map(jwt -> {
            long now = System.currentTimeMillis();
            int recent = recentLimit == null || recentLimit <= 0 ? 10 : recentLimit;

            // Pull a wide page of tasks + orders (no status filter) and aggregate here.
            var taskResp = liveStub(jwt).listTasks(ListTasksRequest.newBuilder().setContext(ctx(jwt))
                    .setFarmId(farmId).setPage(PageRequest.newBuilder().setPageSize(500)).build());
            var orderResp = orderStub(jwt).listOrders(ListOrdersRequest.newBuilder().setContext(ctx(jwt))
                    .setFarmId(farmId).setPage(PageRequest.newBuilder().setPageSize(500)).build());

            int tTotal = 0, tCreated = 0, tAssigned = 0, tAccepted = 0, tCompleted = 0, tCancelled = 0;
            int overdueAccept = 0, overdueReport = 0;
            List<Map<String, Object>> overdueTasks = new ArrayList<>();
            for (LivestockTask t : taskResp.getTasksList()) {
                tTotal++;
                switch (t.getStatus()) {
                    case TASK_STATUS_CREATED -> tCreated++;
                    case TASK_STATUS_ASSIGNED -> tAssigned++;
                    case TASK_STATUS_ACCEPTED -> tAccepted++;
                    case TASK_STATUS_COMPLETED -> tCompleted++;
                    case TASK_STATUS_CANCELLED -> tCancelled++;
                    default -> { }
                }
                boolean isOverdue = false;
                if (t.getStatus() == TaskStatus.TASK_STATUS_ASSIGNED && t.hasAcceptDeadlineAt()
                        && ms(t.getAcceptDeadlineAt()) < now) { overdueAccept++; isOverdue = true; }
                if (t.getStatus() == TaskStatus.TASK_STATUS_ACCEPTED && t.hasReportDueAt()
                        && ms(t.getReportDueAt()) < now) { overdueReport++; isOverdue = true; }
                if (isOverdue && overdueTasks.size() < recent) overdueTasks.add(taskMap(t));
            }

            int oTotal = 0, oCreated = 0, oReserved = 0, oPosted = 0, oCompleted = 0, oFailed = 0, oCancelled = 0;
            List<Map<String, Object>> failedOrders = new ArrayList<>();
            for (FarmOrder o : orderResp.getOrdersList()) {
                oTotal++;
                switch (o.getStatus()) {
                    case ORDER_STATUS_CREATED -> oCreated++;
                    case ORDER_STATUS_STOCK_RESERVED -> oReserved++;
                    case ORDER_STATUS_FINANCE_POSTED -> oPosted++;
                    case ORDER_STATUS_COMPLETED -> oCompleted++;
                    case ORDER_STATUS_FAILED -> { oFailed++; if (failedOrders.size() < recent) failedOrders.add(orderMap(o)); }
                    case ORDER_STATUS_CANCELLED -> oCancelled++;
                    default -> { }
                }
            }

            var taskSummary = new LinkedHashMap<String, Object>();
            taskSummary.put("total", tTotal); taskSummary.put("created", tCreated);
            taskSummary.put("assigned", tAssigned); taskSummary.put("accepted", tAccepted);
            taskSummary.put("completed", tCompleted); taskSummary.put("cancelled", tCancelled);
            taskSummary.put("overdueAccept", overdueAccept); taskSummary.put("overdueReport", overdueReport);

            var orderSummary = new LinkedHashMap<String, Object>();
            orderSummary.put("total", oTotal); orderSummary.put("created", oCreated);
            orderSummary.put("stockReserved", oReserved); orderSummary.put("financePosted", oPosted);
            orderSummary.put("completed", oCompleted); orderSummary.put("failed", oFailed);
            orderSummary.put("cancelled", oCancelled);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("farmId", farmId);
            m.put("generatedAt", (double) now);
            m.put("tasks", taskSummary);
            m.put("orders", orderSummary);
            m.put("overdueTasks", overdueTasks);
            m.put("failedOrders", failedOrders);
            return m;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ---- inventory & finance (read-convenience) ----

    /** Tồn kho tại (item, warehouse). Read-only qua InventoryService.GetStockBalance. */
    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public Mono<Map<String, Object>> warehouseInventory(@Argument String itemId, @Argument String warehouseId) {
        return jwt().map(jwt -> {
            var b = invStub(jwt).getStockBalance(GetStockBalanceRequest.newBuilder()
                    .setContext(ctx(jwt)).setItemId(itemId).setWarehouseId(warehouseId).build()).getBalance();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", b.getItemId());
            m.put("warehouseId", b.getWarehouseId());
            m.put("onHand", qtyMap(b.getOnHand()));
            m.put("reserved", qtyMap(b.getReserved()));
            m.put("available", qtyMap(b.getAvailable()));
            return m;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** Chi phí theo lô. Read-only qua FarmFinanceService.GetBatchCost (service token mang finance:read). */
    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_report:read')")
    public Mono<Map<String, Object>> batchCost(@Argument String farmId, @Argument String batchId) {
        return jwt().map(jwt -> {
            var c = finStub(jwt).getBatchCost(GetBatchCostRequest.newBuilder()
                    .setContext(ctx(jwt)).setFarmId(farmId).setBatchId(batchId).build()).getCost();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("batchId", c.getBatchId());
            m.put("farmId", c.getFarmId());
            m.put("feedCost", moneyMap(c.getFeedCost()));
            m.put("medicineCost", moneyMap(c.getMedicineCost()));
            m.put("laborCost", moneyMap(c.getLaborCost()));
            m.put("otherCost", moneyMap(c.getOtherCost()));
            m.put("totalCost", moneyMap(c.getTotalCost()));
            m.put("animalCount", c.getAnimalCount());
            return m;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** Dòng tiền theo khoảng thời gian. Read-only qua FarmFinanceService.GetCashFlow. */
    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_report:read')")
    public Mono<Map<String, Object>> cashFlow(@Argument String farmId, @Argument Double fromEpochMs, @Argument Double toEpochMs) {
        return jwt().map(jwt -> {
            var req = GetCashFlowRequest.newBuilder().setContext(ctx(jwt)).setFarmId(farmId);
            if (fromEpochMs != null || toEpochMs != null) {
                var range = com.htv.smartfarm.proto.common.v1.DateRange.newBuilder();
                if (fromEpochMs != null) range.setFrom(com.google.protobuf.Timestamp.newBuilder().setSeconds((long) (fromEpochMs / 1000)));
                if (toEpochMs != null) range.setTo(com.google.protobuf.Timestamp.newBuilder().setSeconds((long) (toEpochMs / 1000)));
                req.setPeriod(range);
            }
            var cf = finStub(jwt).getCashFlow(req.build()).getCashFlow();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("farmId", cf.getFarmId());
            m.put("inflow", moneyMap(cf.getInflow()));
            m.put("outflow", moneyMap(cf.getOutflow()));
            m.put("net", moneyMap(cf.getNet()));
            return m;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** Tồn kho dưới ngưỡng đặt lại theo farm. Read-only qua InventoryService.ListLowStock. */
    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_inventory:read')")
    public Mono<List<Map<String, Object>>> lowStock(@Argument String farmId, @Argument Integer limit) {
        return jwt().map(jwt -> {
            var resp = invStub(jwt).listLowStock(ListLowStockRequest.newBuilder().setContext(ctx(jwt))
                    .setFarmId(farmId).setPage(PageRequest.newBuilder().setPageSize(limit == null || limit <= 0 ? 50 : limit)).build());
            List<Map<String, Object>> out = new ArrayList<>();
            for (StockBalance b : resp.getBalancesList()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("itemId", b.getItemId());
                m.put("warehouseId", b.getWarehouseId());
                m.put("onHand", qtyMap(b.getOnHand()));
                m.put("reserved", qtyMap(b.getReserved()));
                m.put("available", qtyMap(b.getAvailable()));
                out.add(m);
            }
            return out;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ---- identity admin (read-only) ----
    // GraphQL is a read-convenience surface: these resolvers reuse the SAME identity REST endpoints
    // and the SAME identity:admin scope the /api/v1/admin/** proxy enforces. No new authority, no
    // write path here — mutations (assign role, create role, grant permission) stay on REST.

    @SuppressWarnings("unchecked")
    private <T> Mono<T> identityGet(Jwt jwt, String path, Class<T> type) {
        return identity.get().uri(path)
                .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getTokenValue())
                .retrieve().bodyToMono(type);
    }

    /** Danh sách người dùng trong tenant + vai trò + trạng thái. Read-only qua identity GET /admin/users. */
    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:admin')")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> users() {
        return jwt().flatMap(jwt -> identityGet(jwt, "/api/v1/admin/users", Map.class).map(body -> {
            Object list = ((Map<String, Object>) body).get("users");
            List<Map<String, Object>> out = new ArrayList<>();
            if (list instanceof List<?> rows) {
                for (Object row : rows) {
                    if (!(row instanceof Map<?, ?> r)) continue;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", str(r.get("id")));
                    m.put("email", str(r.get("email")));
                    m.put("enabled", Boolean.TRUE.equals(r.get("enabled")));
                    m.put("locked", Boolean.TRUE.equals(r.get("locked")));
                    m.put("roles", strList(r.get("roles")));
                    out.add(m);
                }
            }
            return out;
        }));
    }

    /** Mọi vai trò và quyền nó cấp. Read-only qua identity GET /admin/roles ({CODE:[perms]}). */
    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:admin')")
    @SuppressWarnings("unchecked")
    public Mono<List<Map<String, Object>>> roles() {
        return jwt().flatMap(jwt -> identityGet(jwt, "/api/v1/admin/roles", Map.class).map(body -> {
            Object roles = ((Map<String, Object>) body).get("roles");
            List<Map<String, Object>> out = new ArrayList<>();
            if (roles instanceof Map<?, ?> byCode) {
                for (Map.Entry<?, ?> e : byCode.entrySet()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", str(e.getKey()));
                    m.put("permissions", strList(e.getValue()));
                    out.add(m);
                }
            }
            return out;
        }));
    }

    /** Toàn bộ permission/scope hệ thống biết. Read-only qua identity GET /admin/permissions. */
    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:admin')")
    @SuppressWarnings("unchecked")
    public Mono<List<String>> permissions() {
        return jwt().flatMap(jwt -> identityGet(jwt, "/api/v1/admin/permissions", Map.class)
                .map(body -> strList(((Map<String, Object>) body).get("permissions"))));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) if (x != null) out.add(x.toString());
        return out;
    }

    private static Map<String, Object> moneyMap(com.htv.smartfarm.proto.common.v1.Money mon) {
        var m = new LinkedHashMap<String, Object>();
        m.put("currency", mon.getCurrencyCode());
        m.put("minor", (double) mon.getMinorUnits());
        return m;
    }

    private static Map<String, Object> qtyMap(com.htv.smartfarm.proto.common.v1.Quantity q) {
        var m = new LinkedHashMap<String, Object>();
        m.put("value", q.getDecimalValue());
        if (!q.getUnit().isBlank()) m.put("unit", q.getUnit());
        return m;
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
