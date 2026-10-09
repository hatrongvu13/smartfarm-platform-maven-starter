
package com.htv.smartfarm.inventory.application.stock;

import com.htv.smartfarm.inventory.domain.item.ItemJpaRepository;
import com.htv.smartfarm.inventory.domain.lot.LotJpaRepository;
import com.htv.smartfarm.inventory.domain.movement.MovementEntity;
import com.htv.smartfarm.inventory.domain.movement.MovementJpaRepository;
import com.htv.smartfarm.inventory.domain.stock.BalanceJpaRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockAdjustmentService {
    private final LotJpaRepository lots;
    private final ItemJpaRepository items;
    private final BalanceJpaRepository balances;
    private final MovementJpaRepository movements;

    public StockAdjustmentService(LotJpaRepository lots, ItemJpaRepository items,
                                  BalanceJpaRepository balances, MovementJpaRepository movements) {
        this.lots = lots; this.items = items; this.balances = balances; this.movements = movements;
    }

    @Transactional
    public MovementEntity adjust(String tenant, String lotId, String decimal, String unit,
                                 String reason, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw new IllegalArgumentException("idempotency_key required");
        var prior = movements.findByTenantIdAndIdempotencyKey(tenant, idempotencyKey);
        if (prior.isPresent()) return prior.get();
        var lot = lots.findByTenantIdAndId(tenant, lotId)
                .orElseThrow(() -> new IllegalArgumentException("lot not found"));
        var item = items.findByTenantIdAndId(tenant, lot.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("item not found"));
        if (!item.getUnit().equals(unit)) throw new IllegalArgumentException("quantity unit mismatch");
        BigDecimal delta;
        try { delta = new BigDecimal(decimal); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("invalid adjustment quantity", ex); }
        if (delta.signum() == 0 || delta.precision() > 18 || delta.scale() > 3)
            throw new IllegalArgumentException("signed_delta must be non-zero, max 3 decimals/18 digits");
        int updated = delta.signum() > 0
                ? balances.increment(tenant, lotId, delta)
                : balances.decrement(tenant, lotId, delta.abs());
        if (updated != 1) throw new IllegalArgumentException("insufficient stock or balance missing");
        String reference = reason == null ? "" : reason.trim();
        MovementEntity movement = new MovementEntity(UUID.randomUUID().toString(), tenant, lotId,
                "ADJUSTMENT", delta, reference, idempotencyKey);
        return movements.save(movement);
    }
}
