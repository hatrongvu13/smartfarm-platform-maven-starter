package com.htv.farmverse.order.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import com.htv.farmverse.proto.order.v1.GetOrderSagaByOrderRequest;
import com.htv.farmverse.proto.order.v1.OrderSagaAdministrationServiceGrpc;
import org.junit.jupiter.api.Test;

class OrderSagaProtoContractTest {
    @Test
    void sagaAdministrationExposesLookupByOrderId() {
        assertThat(OrderSagaAdministrationServiceGrpc.getServiceDescriptor().getMethods())
                .extracting(method -> method.getBareMethodName())
                .contains("GetOrderSagaByOrder");
        assertThat(GetOrderSagaByOrderRequest.newBuilder().setOrderId("order-1").build().getOrderId())
                .isEqualTo("order-1");
    }
}
