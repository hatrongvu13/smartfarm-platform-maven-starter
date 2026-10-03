package com.htv.smartfarm.inventory.domain;

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
        BalanceEntity.Key a = new BalanceEntity.Key("tenant-001", "lot-001");
        BalanceEntity.Key b = new BalanceEntity.Key("tenant-001", "lot-001");
        BalanceEntity.Key different = new BalanceEntity.Key("tenant-001", "lot-002");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(different);
    }

    @Test
    void compositeKeyDistinguishesTenant() {
        BalanceEntity.Key t1 = new BalanceEntity.Key("tenant-001", "lot-001");
        BalanceEntity.Key t2 = new BalanceEntity.Key("tenant-002", "lot-001");
        assertThat(t1).isNotEqualTo(t2);
    }
}
