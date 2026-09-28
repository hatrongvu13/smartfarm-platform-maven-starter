package com.htv.smartfarm.inventory.domain;

import com.htv.smartfarm.proto.inventory.v1.*;
import com.htv.smartfarm.proto.common.v1.RequestContext;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryCommands {
    private final InventoryRepository repo;

    public InventoryCommands(InventoryRepository repo) {
        this.repo = repo;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }

    private static String key(RequestContext ctx, String tenant) {
        require(ctx != null, "context required");
        require(ctx.getTenantId().isBlank() || tenant.equals(ctx.getTenantId()), "tenant mismatch");
        String k = ctx.getIdempotencyKey();
        require(!k.isBlank() && k.length() <= 128, "idempotency_key required (max 128)");
        return k;
    }

    private static BigDecimal quantity(com.htv.smartfarm.proto.common.v1.Quantity q, String unit) {
        require(q != null && q.getUnit().equals(unit), "quantity unit mismatch");
        try {
            BigDecimal n = new BigDecimal(q.getDecimalValue());
            require(n.signum() > 0 && n.precision() <= 18 && n.scale() <= 3, "quantity must be positive, max 3 decimals/18 digits");
            return n;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid decimal quantity", ex);
        }
    }

    @Transactional
    public InventoryRepository.Item createItem(String tenant, CreateItemRequest req) {
        require(req.hasContext() && req.hasItem(), "item/context required");
        key(req.getContext(), tenant);
        var v = req.getItem();
        require(!v.getSku().isBlank() && !v.getName().isBlank() && !v.getUnit().isBlank(), "sku/name/unit required");
        require(v.getSku().length() <= 80 && v.getName().length() <= 200 && v.getUnit().length() <= 20, "item field too long");
        var old = repo.itemBySku(tenant, v.getSku());
        if (old != null) {
            require(old.name().equals(v.getName()) && old.unit().equals(v.getUnit()) && old.category().equals(v.getCategory().name()), "SKU already exists with different details");
            return old;
        }
        // Optional reorder threshold (same unit as the item); default "0" = no threshold.
        String threshold = "0";
        if (v.hasReorderThreshold()) {
            var rt = v.getReorderThreshold();
            require(rt.getUnit().isBlank() || rt.getUnit().equals(v.getUnit()), "reorder_threshold unit must match item unit");
            if (!rt.getDecimalValue().isBlank()) {
                try {
                    var n = new BigDecimal(rt.getDecimalValue());
                    require(n.signum() >= 0 && n.precision() <= 18 && n.scale() <= 3, "reorder_threshold must be >= 0, max 3 decimals/18 digits");
                    threshold = n.toPlainString();
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("invalid reorder_threshold");
                }
            }
        }
        var item = new InventoryRepository.Item(UUID.randomUUID().toString(), tenant, v.getSku(), v.getName(), v.getUnit(), v.getCategory().name(), threshold);
        repo.createItem(item);
        return item;
    }

    @Transactional(readOnly = true)
    public java.util.List<InventoryRepository.LowBalance> lowStock(String tenant, String farmId, int limit) {
        require(farmId != null && !farmId.isBlank(), "farmId required");
        return repo.lowStock(tenant, farmId, limit <= 0 ? 50 : limit);
    }

    @Transactional
    public InventoryRepository.Movement receive(String tenant, ReceiveStockRequest req) {
        require(req.hasContext() && req.hasLot() && req.hasQuantity(), "context/lot/quantity required");
        String k = key(req.getContext(), tenant);
        var v = req.getLot();
        var item = repo.item(tenant, v.getItemId());
        require(item != null, "unknown item");
        require(!v.getFarmId().isBlank() && !v.getWarehouseId().isBlank() && v.getFarmId().length() <= 100 && v.getWarehouseId().length() <= 100, "farm/warehouse required (max 100)");
        BigDecimal n = quantity(req.getQuantity(), item.unit());
        require(req.getReferenceId().length() <= 128, "reference too long");
        var prior = repo.movement(tenant, k);
        if (prior != null) {
            require(prior.kind().equals("RECEIPT") && prior.lotId().equals(v.getLotId()) && prior.quantity().compareTo(n) == 0 && prior.referenceId().equals(req.getReferenceId()), "idempotency conflict");
            return prior;
        }
        String lotId = v.getLotId().isBlank() ? UUID.randomUUID().toString() : v.getLotId();
        var lot = repo.lot(tenant, lotId);
        if (lot == null) {
            repo.createLot(new InventoryRepository.Lot(lotId, tenant, item.id(), v.getFarmId(), v.getWarehouseId()));
            repo.createBalance(tenant, lotId);
        } else
            require(lot.itemId().equals(item.id()) && lot.farmId().equals(v.getFarmId()) && lot.warehouseId().equals(v.getWarehouseId()), "lot mismatch");
        var m = new InventoryRepository.Movement(UUID.randomUUID().toString(), tenant, lotId, "RECEIPT", n, req.getReferenceId());
        repo.addMovement(m, k);
        require(repo.increment(tenant, lotId, n), "balance missing");
        return m;
    }

    @Transactional
    public InventoryRepository.Movement issue(String tenant, IssueStockRequest req) {
        require(req.hasContext() && req.hasQuantity(), "context/quantity required");
        String k = key(req.getContext(), tenant);
        var lot = repo.lot(tenant, req.getLotId());
        require(lot != null, "unknown lot");
        var item = repo.item(tenant, lot.itemId());
        BigDecimal n = quantity(req.getQuantity(), item.unit());
        require(req.getReferenceId().length() <= 128, "reference too long");
        var prior = repo.movement(tenant, k);
        if (prior != null) {
            require(prior.kind().equals("ISSUE") && prior.lotId().equals(lot.id()) && prior.quantity().compareTo(n) == 0 && prior.referenceId().equals(req.getReferenceId()), "idempotency conflict");
            return prior;
        }
        require(repo.decrement(tenant, lot.id(), n), "insufficient stock");
        var m = new InventoryRepository.Movement(UUID.randomUUID().toString(), tenant, lot.id(), "ISSUE", n, req.getReferenceId());
        repo.addMovement(m, k);
        return m;
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(String tenant, String itemId, String warehouse) {
        require(repo.item(tenant, itemId) != null, "unknown item");
        require(warehouse != null && !warehouse.isBlank(), "warehouse required");
        return repo.balance(tenant, itemId, warehouse);
    }

    @Transactional(readOnly = true)
    public BigDecimal reserved(String tenant, String itemId, String warehouse) {
        require(repo.item(tenant, itemId) != null, "unknown item");
        require(warehouse != null && !warehouse.isBlank(), "warehouse required");
        return repo.reservedTotal(tenant, itemId, warehouse);
    }

    // ---- Saga participant operations: reserve / release / commit ------------

    @Transactional
    public InventoryRepository.Reservation reserve(String tenant, ReserveStockRequest req) {
        require(req.hasContext() && req.hasQuantity(), "context/quantity required");
        String k = key(req.getContext(), tenant);
        require(!req.getOrderId().isBlank(), "order_id required");
        var item = repo.item(tenant, req.getItemId());
        require(item != null, "unknown item");
        require(!req.getWarehouseId().isBlank(), "warehouse_id required");
        BigDecimal n = quantity(req.getQuantity(), item.unit());

        var prior = repo.reservationByKey(tenant, k);
        if (prior != null) {
            require(prior.itemId().equals(item.id()) && prior.quantity().compareTo(n) == 0
                    && prior.warehouseId().equals(req.getWarehouseId()), "idempotency key reused with different reservation");
            return prior;
        }

        // FIFO: reserve from the first lot with enough available stock in this warehouse.
        String chosenLot = null;
        for (String lotId : repo.lotsWithAvailable(tenant, item.id(), req.getWarehouseId())) {
            if (repo.reserveOnLot(tenant, lotId, n)) { chosenLot = lotId; break; }
        }
        require(chosenLot != null, "insufficient available stock to reserve");

        var reservation = new InventoryRepository.Reservation(UUID.randomUUID().toString(), tenant, req.getOrderId(),
                item.id(), chosenLot, req.getWarehouseId(), n, "ACTIVE");
        repo.saveReservation(reservation, k);
        return reservation;
    }

    /** Compensating action: give reserved stock back. Idempotent — releasing a non-active reservation is a no-op. */
    @Transactional
    public InventoryRepository.Reservation release(String tenant, ReleaseReservationRequest req) {
        require(req.hasContext(), "context required");
        key(req.getContext(), tenant);
        require(!req.getReservationId().isBlank(), "reservation_id required");
        var r = repo.reservation(tenant, req.getReservationId());
        require(r != null, "reservation not found");
        if (!"ACTIVE".equals(r.status())) return r; // already released/committed
        require(repo.releaseOnLot(tenant, r.lotId(), r.quantity()), "release failed: reserved balance changed");
        repo.markReservationReleased(tenant, r.id());
        return repo.reservation(tenant, r.id());
    }

    /** Commit a reservation: the held stock is consumed. Idempotent on an already-committed reservation. */
    @Transactional
    public InventoryRepository.Movement commit(String tenant, CommitReservationRequest req) {
        require(req.hasContext(), "context required");
        String k = key(req.getContext(), tenant);
        require(!req.getReservationId().isBlank(), "reservation_id required");
        var r = repo.reservation(tenant, req.getReservationId());
        require(r != null, "reservation not found");
        require(!"RELEASED".equals(r.status()), "cannot commit a released reservation");

        var prior = repo.movement(tenant, k);
        if (prior != null) return prior; // idempotent commit

        if ("ACTIVE".equals(r.status())) {
            require(repo.commitOnLot(tenant, r.lotId(), r.quantity()), "commit failed: balance changed");
            repo.markReservationCommitted(tenant, r.id());
        }
        var m = new InventoryRepository.Movement(UUID.randomUUID().toString(), tenant, r.lotId(),
                "RESERVATION_COMMIT", r.quantity(), r.orderId());
        repo.addMovement(m, k);
        return m;
    }
}