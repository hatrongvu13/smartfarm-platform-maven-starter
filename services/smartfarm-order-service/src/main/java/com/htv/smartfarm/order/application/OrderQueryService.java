package com.htv.smartfarm.order.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.htv.smartfarm.order.domain.OrderDomainStatus;
import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.order.domain.OrderLineEntity;
import com.htv.smartfarm.order.domain.OrderLineJpaRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {

    private final OrderJpaRepository orders;
    private final OrderLineJpaRepository lines;
    private final OrderCursorCodec cursors;

    public OrderQueryService(
            OrderJpaRepository orders,
            OrderLineJpaRepository lines,
            OrderCursorCodec cursors
    ) {
        this.orders = orders;
        this.lines = lines;
        this.cursors = cursors;
    }

    @Transactional(readOnly = true)
    public OrderDraftData get(String tenantId, String orderId) {
        OrderEntity order = orders.findByTenantIdAndId(required(tenantId, "tenantId"), required(orderId, "orderId"))
                .orElseThrow(() -> new IllegalArgumentException("order not found"));
        return map(order, lines.findByOrderIdOrderByLineNo(order.getId()));
    }

    @Transactional(readOnly = true)
    public OrderPage list(OrderQueryFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter is required");
        String tenantId = required(filter.tenantId(), "tenantId");
        int pageSize = filter.pageSize() <= 0 ? 50 : Math.min(filter.pageSize(), 200);
        Long createdFrom = filter.createdFrom();
        Long createdTo = filter.createdTo();
        if (createdFrom != null && createdFrom < 0) throw new IllegalArgumentException("createdFrom is invalid");
        if (createdTo != null && createdTo < 0) throw new IllegalArgumentException("createdTo is invalid");
        if (createdFrom != null && createdTo != null && createdFrom > createdTo) {
            throw new IllegalArgumentException("createdFrom must not be after createdTo");
        }
        String status = normalizeStatus(filter.status());
        OrderCursorCodec.Cursor cursor = cursors.decode(filter.pageToken());
        List<OrderEntity> fetched = orders.listCursor(
                tenantId,
                nullable(filter.farmId()),
                status,
                nullable(filter.warehouseId()),
                createdFrom,
                createdTo,
                cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.orderId(),
                PageRequest.of(0, pageSize + 1)
        );
        boolean hasMore = fetched.size() > pageSize;
        List<OrderEntity> pageRows = hasMore
                ? List.copyOf(fetched.subList(0, pageSize))
                : List.copyOf(fetched);
        Map<String, List<OrderLineEntity>> groupedLines = loadLines(pageRows);
        List<OrderDraftData> values = pageRows.stream()
                .map(order -> map(order, groupedLines.getOrDefault(order.getId(), List.of())))
                .toList();
        String next = "";
        if (hasMore && !pageRows.isEmpty()) {
            OrderEntity last = pageRows.get(pageRows.size() - 1);
            next = cursors.encode(last.getCreatedAt(), last.getId());
        }
        return new OrderPage(values, next);
    }

    private Map<String, List<OrderLineEntity>> loadLines(List<OrderEntity> pageRows) {
        if (pageRows.isEmpty()) return Map.of();
        List<String> ids = pageRows.stream().map(OrderEntity::getId).toList();
        Map<String, List<OrderLineEntity>> grouped = new LinkedHashMap<>();
        for (OrderLineEntity line : lines.findByOrderIdInOrderByOrderIdAscLineNoAsc(ids)) {
            grouped.computeIfAbsent(line.getOrderId(), ignored -> new ArrayList<>()).add(line);
        }
        return grouped;
    }

    private OrderDraftData map(OrderEntity order, List<OrderLineEntity> rows) {
        List<OrderDraftLineData> lineData = rows.stream()
                .map(value -> new OrderDraftLineData(
                        value.getId(), value.getLineNo(), value.getItemId(), value.getWarehouseId(),
                        value.getQuantity(), value.getQuantityUnit(), value.getUnitPriceMinor(),
                        value.getLineTotalMinor(), value.getVersion()))
                .toList();
        return new OrderDraftData(
                order.getId(), order.getTenantId(), order.getFarmId(), order.getBatchId(),
                order.getCurrencyCode(), order.getTotalMinor(), order.getStatus(), order.getVersion(),
                order.getCreatedAt(), order.getUpdatedAt(), lineData);
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return OrderDomainStatus.fromStored(normalized).protoName();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
