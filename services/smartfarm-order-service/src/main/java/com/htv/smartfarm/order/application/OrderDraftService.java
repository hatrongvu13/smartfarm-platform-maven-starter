package com.htv.smartfarm.order.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.htv.smartfarm.order.domain.OrderDomainStatus;
import com.htv.smartfarm.order.domain.OrderEntity;
import com.htv.smartfarm.order.domain.OrderJpaRepository;
import com.htv.smartfarm.order.domain.OrderLineEntity;
import com.htv.smartfarm.order.domain.OrderLineJpaRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderDraftService {

    private final OrderJpaRepository orders;
    private final OrderLineJpaRepository lines;
    private final Clock clock;

    @Autowired
    public OrderDraftService(
            OrderJpaRepository orders,
            OrderLineJpaRepository lines,
            Clock clock
    ) {
        this.orders = orders;
        this.lines = lines;
        this.clock = clock;
    }

    @Transactional
    public OrderDraftData create(CreateOrderDraftCommand command) {
        requireCreate(command);
        var existing = orders.findByTenantIdAndIdempotencyKey(
                command.tenantId(), command.idempotencyKey()).orElse(null);
        if (existing != null) return data(existing);

        var validated = OrderDraftValidation.validate(command.lines());
        String orderId = UUID.randomUUID().toString();
        long now = clock.millis();
        var first = validated.lines().get(0);
        OrderEntity order = new OrderEntity(
                orderId,
                OrderDraftValidation.required(command.tenantId(), "tenantId"),
                OrderDraftValidation.required(command.farmId(), "farmId"),
                OrderDraftValidation.nullable(command.batchId()),
                first.itemId(), first.warehouseId(), first.quantity(), first.quantityUnit(),
                validated.currencyCode(), validated.totalMinor(),
                OrderDomainStatus.DRAFT.protoName(), now,
                OrderDraftValidation.required(command.idempotencyKey(), "idempotencyKey"),
                OrderDraftValidation.required(command.actorId(), "actorId")
        );
        try {
            orders.saveAndFlush(order);
            lines.saveAllAndFlush(toEntities(orderId, validated.lines()));
        } catch (DataIntegrityViolationException conflict) {
            OrderEntity concurrent = orders.findByTenantIdAndIdempotencyKey(
                    command.tenantId(), command.idempotencyKey()).orElseThrow(() -> conflict);
            return data(concurrent);
        }
        return data(order);
    }

    @Transactional
    public OrderDraftData update(UpdateOrderDraftCommand command) {
        requireUpdate(command);
        OrderEntity order = draftForUpdate(command.tenantId(), command.orderId());
        requireVersion(order, command.expectedVersion());
        var validated = OrderDraftValidation.validate(command.lines());
        var first = validated.lines().get(0);
        order.replaceDraftHeader(
                command.farmId(), command.batchId(),
                first.itemId(), first.warehouseId(), first.quantity(), first.quantityUnit(),
                validated.currencyCode(), validated.totalMinor(), command.actorId(), clock.millis());
        lines.deleteByOrderId(order.getId());
        lines.flush();
        lines.saveAll(toEntities(order.getId(), validated.lines()));
        orders.flush();
        return data(order);
    }

    @Transactional
    public OrderDraftData submit(
            String tenantId,
            String orderId,
            long expectedVersion,
            String actorId
    ) {
        OrderEntity order = draftForUpdate(tenantId, orderId);
        requireVersion(order, expectedVersion);
        if (lines.findByOrderIdOrderByLineNo(orderId).isEmpty()) {
            throw new IllegalStateException("draft order must contain at least one line");
        }
        order.submit(OrderDraftValidation.required(actorId, "actorId"), clock.millis());
        orders.flush();
        return data(order);
    }

    @Transactional
    public void delete(
            String tenantId,
            String orderId,
            long expectedVersion
    ) {
        OrderEntity order = draftForUpdate(tenantId, orderId);
        requireVersion(order, expectedVersion);
        lines.deleteByOrderId(orderId);
        orders.delete(order);
        orders.flush();
    }

    @Transactional(readOnly = true)
    public OrderDraftData get(String tenantId, String orderId) {
        OrderEntity order = orders.findByTenantIdAndId(tenantId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("order not found"));
        return data(order);
    }

    private OrderEntity draftForUpdate(String tenantId, String orderId) {
        OrderEntity order = orders.findByTenantIdAndIdForUpdate(
                OrderDraftValidation.required(tenantId, "tenantId"),
                OrderDraftValidation.required(orderId, "orderId"))
                .orElseThrow(() -> new IllegalArgumentException("order not found"));
        if (!order.domainStatus().editable()) {
            throw new IllegalStateException("only draft orders can be changed");
        }
        return order;
    }

    private void requireVersion(OrderEntity order, long expectedVersion) {
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        if (order.getVersion() != expectedVersion) {
            throw new OrderVersionConflictException(expectedVersion, order.getVersion());
        }
    }

    private List<OrderLineEntity> toEntities(
            String orderId,
            List<OrderDraftValidation.ValidatedLine> values
    ) {
        java.util.ArrayList<OrderLineEntity> result = new java.util.ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            var value = values.get(index);
            result.add(new OrderLineEntity(
                    UUID.randomUUID().toString(), orderId, index,
                    value.itemId(), value.warehouseId(), value.quantity(), value.quantityUnit(),
                    value.unitPriceMinor(), value.lineTotalMinor()));
        }
        return result;
    }

    private OrderDraftData data(OrderEntity order) {
        List<OrderDraftLineData> lineData = lines.findByOrderIdOrderByLineNo(order.getId()).stream()
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

    private void requireCreate(CreateOrderDraftCommand command) {
        if (command == null) throw new IllegalArgumentException("create draft command is required");
        OrderDraftValidation.required(command.tenantId(), "tenantId");
        OrderDraftValidation.required(command.actorId(), "actorId");
        OrderDraftValidation.required(command.idempotencyKey(), "idempotencyKey");
        OrderDraftValidation.required(command.farmId(), "farmId");
    }

    private void requireUpdate(UpdateOrderDraftCommand command) {
        if (command == null) throw new IllegalArgumentException("update draft command is required");
        OrderDraftValidation.required(command.tenantId(), "tenantId");
        OrderDraftValidation.required(command.actorId(), "actorId");
        OrderDraftValidation.required(command.orderId(), "orderId");
        OrderDraftValidation.required(command.farmId(), "farmId");
    }
}
