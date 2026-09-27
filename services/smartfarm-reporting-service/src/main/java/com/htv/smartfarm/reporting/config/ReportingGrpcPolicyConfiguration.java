package com.htv.smartfarm.reporting.config;

import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;

import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Per-method authorization for reporting gRPC. Requesting an export requires
 * {@code report:write}; reading jobs / download locations requires {@code report:read}.
 */
@Configuration(proxyBeanMethods = false)
public class ReportingGrpcPolicyConfiguration {

    @Bean
    GrpcMethodPolicy reportingGrpcMethodPolicy() {
        return new GrpcMethodPolicy(
                Map.of(
                        "smartfarm.reporting.v1.ReportingService/RequestExport", "SCOPE_report:write",
                        "smartfarm.reporting.v1.ReportingService/GetExportJob", "SCOPE_report:read",
                        "smartfarm.reporting.v1.ReportingService/ListExportJobs", "SCOPE_report:read",
                        "smartfarm.reporting.v1.ReportingService/GetDownloadLocation", "SCOPE_report:read"),
                Set.of("grpc.health.v1.Health/Check", "grpc.health.v1.Health/Watch"));
    }
}
