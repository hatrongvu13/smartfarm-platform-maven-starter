package com.htv.farmverse.farm.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Luật của aggregate, chạy không cần Spring. */
class FarmTest {

    @Test
    void barnsCountTowardsCapacityAndCodesAreUnique() {
        Farm farm = Farm.create("green-valley", "Green Valley", null);
        farm.addBarn("B-1", "Chuồng bò", 40);
        farm.addBarn("B-2", "Chuồng gà", 500);

        assertThat(farm.totalCapacity()).isEqualTo(540);
        assertThatThrownBy(() -> farm.addBarn("B-1", "Trùng", 10))
                .isInstanceOf(FarmRuleViolation.class)
                .extracting("code").isEqualTo("SF-FARM-007");
    }

    @Test
    void archivedFarmRejectsNewBarns() {
        Farm farm = Farm.create("old-farm", "Old", null);
        farm.archive();

        assertThatThrownBy(() -> farm.addBarn("B-1", "Chuồng", 10))
                .extracting("code").isEqualTo("SF-FARM-006");
    }

    @Test
    void invalidValuesAreRejected() {
        assertThatThrownBy(() -> Farm.create("Bad Code", "x", null)).extracting("code").isEqualTo("SF-FARM-001");
        Farm farm = Farm.create("ok-farm", "Ok", null);
        assertThatThrownBy(() -> farm.addBarn("b1", "lowercase", 1)).extracting("code").isEqualTo("SF-FARM-003");
        assertThatThrownBy(() -> farm.addBarn("B1", "zero", 0)).extracting("code").isEqualTo("SF-FARM-005");
    }
}
