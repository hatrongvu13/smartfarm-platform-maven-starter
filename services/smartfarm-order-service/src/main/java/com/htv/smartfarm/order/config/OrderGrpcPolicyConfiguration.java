package com.htv.smartfarm.order.config;

import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;

import java.time.Clock;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Per-method authorization for the order/saga gRPC surface. PlaceOrder requires
 * {@code orders:write}; reads require {@code orders:read}.
 */
@Configuration(proxyBeanMethods = false)
public class OrderGrpcPolicyConfiguration {

    @Bean
    GrpcMethodPolicy orderGrpcMethodPolicy() {
        return new GrpcMethodPolicy(
                Map.of(
                        "smartfarm.order.v1.FarmOrderService/PlaceOrder", "SCOPE_orders:write",
                        "smartfarm.order.v1.FarmOrderService/GetOrder", "SCOPE_orders:read",
                        "smartfarm.order.v1.FarmOrderService/ListOrders", "SCOPE_orders:read",
                        "smartfarm.order.v1.FarmOrderService/CancelOrder", "SCOPE_orders:write",
                        "smartfarm.order.v1.FarmOrderService/CreateDraftOrder", "SCOPE_orders:write",
                        "smartfarm.order.v1.FarmOrderService/UpdateDraftOrder", "SCOPE_orders:write",
                        "smartfarm.order.v1.FarmOrderService/DeleteDraftOrder", "SCOPE_orders:write",
                        "smartfarm.order.v1.FarmOrderService/SubmitDraftOrder", "SCOPE_orders:write"),
                Set.of("grpc.health.v1.Health/Check", "grpc.health.v1.Health/Watch"));
    }

    @Bean
    Clock orderClock() {
        return Clock.systemUTC();
    }

}
