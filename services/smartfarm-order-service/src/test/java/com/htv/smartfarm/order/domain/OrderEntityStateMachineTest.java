package com.htv.smartfarm.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderEntityStateMachineTest {
    @Test
    void draftShouldSubmitAndTrackAuditTime() {
        OrderEntity order = order(OrderDomainStatus.DRAFT);
        order.submit("user-002", 1100L);
        assertThat(order.domainStatus()).isEqualTo(OrderDomainStatus.CREATED);
        assertThat(order.getUpdatedAt()).isEqualTo(1100L);
        assertThat(order.getUpdatedBy()).isEqualTo("user-002");
    }

    @Test
    void onlyDraftCanReplaceHeader() {
        OrderEntity order = order(OrderDomainStatus.CREATED);
        assertThatThrownBy(() -> order.replaceDraftHeader(
                "farm-002", null, "item-002", "warehouse-002", "2", "KG",
                "VND", 20000L, "user-001", 1100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("only draft orders can be edited");
    }

    private OrderEntity order(OrderDomainStatus status) {
        return new OrderEntity("order-001", "tenant-001", "farm-001", null,
                "item-001", "warehouse-001", "1", "KG", "VND", 10000L,
                status.protoName(), 1000L, "idem-001", "user-001");
    }
}
