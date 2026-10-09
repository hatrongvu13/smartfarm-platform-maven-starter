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
    private final com.htv.smartfarm.inventory.application.warehouse.WarehouseApplicationService warehouses;

    private final com.htv.smartfarm.inventory.application.query.InventoryQueryService queries;
    private final com.htv.smartfarm.inventory.application.stock.StockAdjustmentService adjustments;
    private final com.htv.smartfarm.inventory.application.catalog.InventoryCatalogService catalog;
    public InventoryGrpcService(InventoryCommands commands, InventoryRepository repo,
                                com.htv.smartfarm.inventory.application.warehouse.WarehouseApplicationService warehouses,
            com.htv.smartfarm.inventory.application.query.InventoryQueryService queries,
            com.htv.smartfarm.inventory.application.stock.StockAdjustmentService adjustments,
            com.htv.smartfarm.inventory.application.catalog.InventoryCatalogService catalog) {
        this.commands = commands;
        this.repo = repo;
        this.warehouses = warehouses;
        this.queries = queries;
        this.adjustments = adjustments;
        this.catalog = catalog;
    }

    private String tenant() {
        return GrpcSecurityContext.requireTenant();
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

    @Override
    public void createWarehouse(CreateWarehouseRequest req, StreamObserver<WarehouseResponse> out) {
        respond(out, () -> WarehouseResponse.newBuilder().setWarehouse(WarehouseProtoMapper.proto(warehouses.create(tenant(), req.getContext().getActorId(), req.getWarehouse().getWarehouseId(), req.getWarehouse().getFarmId(), req.getWarehouse().getCode(), req.getWarehouse().getName(), req.getWarehouse().getDescription(), req.getWarehouse().getAddress()))).build());
    }

    @Override
    public void getWarehouse(GetWarehouseRequest req, StreamObserver<WarehouseResponse> out) {
        respond(out, () -> WarehouseResponse.newBuilder().setWarehouse(WarehouseProtoMapper.proto(warehouses.get(tenant(), req.getWarehouseId()))).build());
    }

    @Override
    public void listWarehouses(ListWarehousesRequest req, StreamObserver<ListWarehousesResponse> out) {
        respond(out, () -> {
            var b = ListWarehousesResponse.newBuilder();
            var s = req.getStatus() == com.htv.smartfarm.proto.inventory.v1.WarehouseStatus.WAREHOUSE_STATUS_UNSPECIFIED ? null : com.htv.smartfarm.inventory.domain.warehouse.WarehouseStatus.valueOf(req.getStatus().name().replace("WAREHOUSE_STATUS_", ""));
            warehouses.list(tenant(), req.getFarmId(), s, req.getQuery(), req.hasPage() ? req.getPage().getPageSize() : 50).forEach(v -> b.addWarehouses(WarehouseProtoMapper.proto(v)));
            return b.build();
        });
    }

    @Override
    public void listWarehouseItems(ListWarehouseItemsRequest req, StreamObserver<ListWarehouseItemsResponse> out) {
        respond(out, () -> {
            var b = ListWarehouseItemsResponse.newBuilder().setWarehouse(WarehouseProtoMapper.proto(warehouses.get(tenant(), req.getWarehouseId())));
            warehouses.items(tenant(), req.getWarehouseId(), req.getQuery(), req.getLowStockOnly(), req.hasPage() ? req.getPage().getPageSize() : 50).forEach(v -> b.addItems(WarehouseProtoMapper.item(v)));
            return b.build();
        });
    }



    @Override public void getItem(GetItemRequest req, StreamObserver<ItemResponse> out) {
        respond(out, () -> ItemResponse.newBuilder().setItem(
                InventoryProtoMapper.item(queries.item(tenant(), req.getItemId()))).build());
    }
    @Override public void listItems(ListItemsRequest req, StreamObserver<ListItemsResponse> out) {
        respond(out, () -> { var b=ListItemsResponse.newBuilder(); String category=req.getCategory()==ItemCategory.ITEM_CATEGORY_UNSPECIFIED?"":req.getCategory().name(); int size=req.hasPage()?req.getPage().getPageSize():50; queries.items(tenant(),req.getQuery(),category,size).forEach(v->b.addItems(InventoryProtoMapper.item(v))); return b.build(); });
    }
    @Override public void adjustStock(AdjustStockRequest req, StreamObserver<StockMovementResponse> out) {
        respond(out, () -> { String t=tenant(); var m=adjustments.adjust(t,req.getLotId(),req.getSignedDelta().getDecimalValue(),req.getSignedDelta().getUnit(),req.getReason(),req.getContext().getIdempotencyKey()); var lot=repo.lot(t,m.getLotId()); return StockMovementResponse.newBuilder().setMovement(InventoryProtoMapper.movement(m,queries.trace(t,m.getLotId(),1).lot(),repo.item(t,lot.itemId()).unit())).build(); });
    }
    @Override public void traceLot(TraceLotRequest req, StreamObserver<TraceLotResponse> out) {
        respond(out, () -> { String t=tenant(); int size=req.hasPage()?req.getPage().getPageSize():50; var trace=queries.trace(t,req.getLotId(),size); var item=queries.item(t,trace.lot().getItemId()); var b=TraceLotResponse.newBuilder().setLot(InventoryProtoMapper.lot(trace.lot())); trace.movements().forEach(v->b.addMovements(InventoryProtoMapper.movement(v,trace.lot(),item.getUnit()))); return b.build(); });
    }


    @Override public void updateItem(UpdateItemRequest req,StreamObserver<ItemResponse> out){respond(out,()->ItemResponse.newBuilder().setItem(InventoryProtoMapper.item(catalog.updateItem(tenant(),req.getItemId(),req.getName(),req.getUnit(),req.getCategory().name(),req.getReorderThreshold().getDecimalValue()))).build());}
    @Override public void setItemStatus(SetItemStatusRequest req,StreamObserver<ItemResponse> out){respond(out,()->ItemResponse.newBuilder().setItem(InventoryProtoMapper.item(catalog.setItemActive(tenant(),req.getItemId(),req.getStatus()==ItemStatus.ITEM_STATUS_ACTIVE))).build());}
    @Override public void updateWarehouse(UpdateWarehouseRequest req,StreamObserver<WarehouseResponse> out){respond(out,()->WarehouseResponse.newBuilder().setWarehouse(WarehouseProtoMapper.proto(catalog.updateWarehouse(tenant(),req.getWarehouseId(),req.getName(),req.getDescription(),req.getAddress()))).build());}
    @Override public void setWarehouseStatus(SetWarehouseStatusRequest req,StreamObserver<WarehouseResponse> out){respond(out,()->WarehouseResponse.newBuilder().setWarehouse(WarehouseProtoMapper.proto(catalog.setWarehouseActive(tenant(),req.getWarehouseId(),req.getStatus()==com.htv.smartfarm.proto.inventory.v1.WarehouseStatus.WAREHOUSE_STATUS_ACTIVE))).build());}
}
