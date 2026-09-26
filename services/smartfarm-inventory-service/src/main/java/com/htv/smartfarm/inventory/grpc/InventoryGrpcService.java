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
            var n = commands.balance(t, req.getItemId(), req.getWarehouseId());
            var q = Quantity.newBuilder().setDecimalValue(n.toPlainString()).setUnit(item.unit());
            return StockBalanceResponse.newBuilder().setBalance(StockBalance.newBuilder().setItemId(item.id()).setWarehouseId(req.getWarehouseId()).setOnHand(q).setAvailable(q).setReserved(Quantity.newBuilder().setDecimalValue("0").setUnit(item.unit()))).build();
        });
    }
}
