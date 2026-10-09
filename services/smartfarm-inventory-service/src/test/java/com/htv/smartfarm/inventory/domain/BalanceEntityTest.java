package com.htv.smartfarm.inventory.domain;

import com.htv.smartfarm.inventory.domain.stock.BalanceEntity;
import com.htv.smartfarm.inventory.domain.stock.BalanceId;

import com.htv.smartfarm.inventory.domain.stock.BalanceEntity;
import com.htv.smartfarm.inventory.domain.stock.BalanceId;

import com.htv.smartfarm.inventory.domain.stock.BalanceEntity;
import com.htv.smartfarm.inventory.domain.stock.BalanceId;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the stock-balance aggregate: a new balance starts with the given on-hand
 * and ZERO reserved, and the composite (tenant, lot) key obeys value equality so JPA can
 * use it as an {@code @IdClass}. Pure domain — no Spring context, no database.
 */
class BalanceEntityTest {

    @Test
    void newBalanceStartsWithZeroReserved() {
        BalanceEntity balance = new BalanceEntity("tenant-001", "lot-001", new BigDecimal("10.000"));
        assertThat(balance.getOnHand()).isEqualByComparingTo("10.000");
        assertThat(balance.getReserved()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void compositeKeyUsesValueEquality() {
        BalanceId a = new BalanceId("tenant-001", "lot-001");
        BalanceId b = new BalanceId("tenant-001", "lot-001");
        BalanceId different = new BalanceId("tenant-001", "lot-002");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(different);
    }

    @Test
    void compositeKeyDistinguishesTenant() {
        BalanceId t1 = new BalanceId("tenant-001", "lot-001");
        BalanceId t2 = new BalanceId("tenant-002", "lot-001");
        assertThat(t1).isNotEqualTo(t2);
    }
}
