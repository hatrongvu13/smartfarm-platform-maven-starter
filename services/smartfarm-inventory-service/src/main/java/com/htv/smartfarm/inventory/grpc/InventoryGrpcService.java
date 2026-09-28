package com.htv.smartfarm.inventory.grpc;

import com.htv.smartfarm.inventory.domain.*;
import com.htv.smartfarm.security.grpc.GrpcSecurityContext;
import com.htv.smartfarm.proto.inventory.v1.*;
import com.htv.smartfarm.proto.common.v1.Quantity;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.util.function.Supplier;

import org.springframework.stereotype.Service;

@Service
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {
    private final InventoryCommands commands;
    private final InventoryRepository repo;

    public InventoryGrpcService(InventoryCommands commands, InventoryRepository repo) {
        this.commands = commands;
        this.repo = repo;
    }

    private String tenant() {
        String t = GrpcSecurityContext.TENANT.get();
        if (t == null || t.isBlank()) throw Status.UNAUTHENTICATED.asRuntimeException();
        return t;
    }

    private static <T> void respond(StreamObserver<T> out, Supplier<T> action) {
        try {
            out.onNext(action.get());
            out.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            out.onError(e);
        } catch (IllegalArgumentException e) {
            out.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            out.onError(Status.ALREADY_EXISTS.withDescription("duplicate key").asRuntimeException());
        } catch (RuntimeException e) {
            out.onError(Status.INTERNAL.withDescription("inventory operation failed").asRuntimeException());
        }
    }

    @Override
    public void createItem(CreateItemRequest req, StreamObserver<ItemResponse> out) {
        respond(out, () -> {
            var i = commands.createItem(tenant(), req);
            return ItemResponse.newBuilder().setItem(InventoryItem.newBuilder().setItemId(i.id()).setSku(i.sku()).setName(i.name()).setUnit(i.unit()).setCategory(ItemCategory.valueOf(i.category()))).build();
        });
    }

    private StockMovementResponse movement(InventoryRepository.Movement m) {
        return StockMovementResponse.newBuilder().setMovement(StockMovement.newBuilder().setMovementId(m.id()).setLotId(m.lotId()).setItemId(repo.lot(tenant(), m.lotId()).itemId()).setWarehouseId(repo.lot(tenant(), m.lotId()).warehouseId()).setType(m.kind().equals("RECEIPT") ? MovementType.MOVEMENT_TYPE_RECEIPT : MovementType.MOVEMENT_TYPE_ISSUE).setQuantity(Quantity.newBuilder().setDecimalValue(m.quantity().toPlainString()).setUnit(repo.item(tenant(), repo.lot(tenant(), m.lotId()).itemId()).unit())).setReferenceId(m.referenceId() == null ? "" : m.referenceId())).build();
    }

    @Override
    public void receiveStock(ReceiveStockRequest req, StreamObserver<StockMovementResponse> out) {
        respond(out, () -> movement(commands.receive(tenant(), req)));
    }

    @Override
    public void issueStock(IssueStockRequest req, StreamObserver<StockMovementResponse> out) {
        respond(out, () -> movement(commands.issue(tenant(), req)));
    }

    @Override
    public void getStockBalance(GetStockBalanceRequest req, StreamObserver<StockBalanceResponse> out) {
        respond(out, () -> {
            var t = tenant();
            var item = repo.item(t, req.getItemId());
            var onHand = commands.balance(t, req.getItemId(), req.getWarehouseId());
            var reserved = commands.reserved(t, req.getItemId(), req.getWarehouseId());
            var available = onHand.subtract(reserved);
            var unit = item.unit();
            return StockBalanceResponse.newBuilder().setBalance(StockBalance.newBuilder()
                    .setItemId(item.id()).setWarehouseId(req.getWarehouseId())
                    .setOnHand(qty(onHand, unit)).setReserved(qty(reserved, unit)).setAvailable(qty(available, unit))).build();
        });
    }

    private static Quantity.Builder qty(java.math.BigDecimal n, String unit) {
        return Quantity.newBuilder().setDecimalValue(n.toPlainString()).setUnit(unit);
    }

    @Override
    public void listLowStock(ListLowStockRequest req, StreamObserver<ListLowStockResponse> out) {
        respond(out, () -> {
            var t = tenant();
            int limit = req.hasPage() && req.getPage().getPageSize() > 0 ? req.getPage().getPageSize() : 50;
            var resp = ListLowStockResponse.newBuilder();
            for (var r : commands.lowStock(t, req.getFarmId(), limit)) {
                var available = r.onHand().subtract(r.reserved());
                resp.addBalances(StockBalance.newBuilder()
                        .setItemId(r.itemId()).setWarehouseId(r.warehouseId())
                        .setOnHand(qty(r.onHand(), r.unit()))
                        .setReserved(qty(r.reserved(), r.unit()))
                        .setAvailable(qty(available, r.unit())));
            }
            return resp.build();
        });
    }

    private ReservationResponse reservation(InventoryRepository.Reservation r) {
        var status = switch (r.status()) {
            case "COMMITTED" -> ReservationStatus.RESERVATION_STATUS_COMMITTED;
            case "RELEASED" -> ReservationStatus.RESERVATION_STATUS_RELEASED;
            default -> ReservationStatus.RESERVATION_STATUS_ACTIVE;
        };
        var item = repo.item(tenant(), r.itemId());
        return ReservationResponse.newBuilder().setReservation(StockReservation.newBuilder()
                .setReservationId(r.id()).setOrderId(r.orderId()).setItemId(r.itemId()).setLotId(r.lotId())
                .setWarehouseId(r.warehouseId()).setStatus(status)
                .setQuantity(qty(r.quantity(), item == null ? "" : item.unit()))).build();
    }

    @Override
    public void reserveStock(ReserveStockRequest req, StreamObserver<ReservationResponse> out) {
        respond(out, () -> reservation(commands.reserve(tenant(), req)));
    }

    @Override
    public void releaseReservation(ReleaseReservationRequest req, StreamObserver<ReservationResponse> out) {
        respond(out, () -> reservation(commands.release(tenant(), req)));
    }

    @Override
    public void commitReservation(CommitReservationRequest req, StreamObserver<StockMovementResponse> out) {
        respond(out, () -> {
            var m = commands.commit(tenant(), req);
            var lot = repo.lot(tenant(), m.lotId());
            return StockMovementResponse.newBuilder().setMovement(StockMovement.newBuilder()
                    .setMovementId(m.id()).setLotId(m.lotId()).setItemId(lot.itemId()).setWarehouseId(lot.warehouseId())
                    .setType(MovementType.MOVEMENT_TYPE_RESERVATION_COMMIT)
                    .setQuantity(qty(m.quantity(), repo.item(tenant(), lot.itemId()).unit()))
                    .setReferenceId(m.referenceId() == null ? "" : m.referenceId())).build();
        });
    }
}
