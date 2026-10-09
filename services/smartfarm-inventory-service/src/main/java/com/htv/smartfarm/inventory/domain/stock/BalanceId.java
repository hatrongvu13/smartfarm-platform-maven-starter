package com.htv.smartfarm.inventory.domain.stock;

import java.io.Serializable;
import java.util.Objects;

public final class BalanceId implements Serializable {
    private String tenantId;
    private String lotId;

    public BalanceId() {
    }

    public BalanceId(String tenantId, String lotId) {
        this.tenantId = require(tenantId, "tenantId");
        this.lotId = require(lotId, "lotId");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BalanceId)) return false;
        BalanceId x = (BalanceId) o;
        return Objects.equals(tenantId, x.tenantId) && Objects.equals(lotId, x.lotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, lotId);
    }
}
