package com.htv.smartfarm.order.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class OrderGrpcPolicyConfigurationTest {
    @Test
    void sagaInspectionIsReadAndRecoveryIsAdmin() {
        var policy = new OrderGrpcPolicyConfiguration().orderGrpcMethodPolicy();
        assertThat(policy.requiredAuthority(
                "smartfarm.order.v1.OrderSagaAdministrationService/GetOrderSaga"))
                .isEqualTo("SCOPE_orders:saga:read");
        assertThat(policy.requiredAuthority(
                "smartfarm.order.v1.OrderSagaAdministrationService/GetOrderSagaByOrder"))
                .isEqualTo("SCOPE_orders:saga:read");
        assertThat(policy.requiredAuthority(
                "smartfarm.order.v1.OrderSagaAdministrationService/RetryOrderSagaStep"))
                .isEqualTo("SCOPE_orders:saga:admin");
    }
}
