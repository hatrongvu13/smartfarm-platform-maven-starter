package com.htv.smartfarm.finance.config;

import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Per-method authorization for finance gRPC. Write operations require
 * {@code finance:write}; reads require {@code finance:read}.
 */
@Configuration(proxyBeanMethods = false)
public class FinanceGrpcPolicyConfiguration {

    @Bean
    GrpcMethodPolicy financeGrpcMethodPolicy() {
        return new GrpcMethodPolicy(
                Map.ofEntries(
                        entry("smartfarm.finance.v1.FarmFinanceService/RecordExpense", "SCOPE_finance:write"),
                        entry("smartfarm.finance.v1.FarmFinanceService/ReverseExpense", "SCOPE_finance:write"),
                        entry("smartfarm.finance.v1.FarmFinanceService/RecordIncome", "SCOPE_finance:write"),
                        entry("smartfarm.finance.v1.FarmFinanceService/RecordPayable", "SCOPE_finance:write"),
                        entry("smartfarm.finance.v1.FarmFinanceService/RecordReceivable", "SCOPE_finance:write"),
                        entry("smartfarm.finance.v1.FarmFinanceService/SettleDebt", "SCOPE_finance:write"),
                        entry("smartfarm.finance.v1.FarmFinanceService/GetTransaction", "SCOPE_finance:read"),
                        entry("smartfarm.finance.v1.FarmFinanceService/ListTransactions", "SCOPE_finance:read"),
                        entry("smartfarm.finance.v1.FarmFinanceService/GetBatchCost", "SCOPE_finance:read"),
                        entry("smartfarm.finance.v1.FarmFinanceService/GetCashFlow", "SCOPE_finance:read")),
                Set.of("grpc.health.v1.Health/Check", "grpc.health.v1.Health/Watch"));
    }
}
