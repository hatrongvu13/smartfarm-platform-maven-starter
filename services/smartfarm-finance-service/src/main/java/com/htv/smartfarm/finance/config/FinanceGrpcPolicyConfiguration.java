package com.htv.smartfarm.finance.config;

import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;

import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Per-method authorization for finance gRPC. Write operations require
 * {@code finance:write}; the saga's service token is granted exactly this scope
 * for the {@code smartfarm-finance} audience.
 */
@Configuration(proxyBeanMethods = false)
public class FinanceGrpcPolicyConfiguration {

    @Bean
    GrpcMethodPolicy financeGrpcMethodPolicy() {
        return new GrpcMethodPolicy(
                Map.of(
                        "smartfarm.finance.v1.FarmFinanceService/RecordExpense", "SCOPE_finance:write",
                        "smartfarm.finance.v1.FarmFinanceService/RecordPayable", "SCOPE_finance:write",
                        "smartfarm.finance.v1.FarmFinanceService/SettleDebt", "SCOPE_finance:write"),
                Set.of("grpc.health.v1.Health/Check", "grpc.health.v1.Health/Watch"));
    }
}
