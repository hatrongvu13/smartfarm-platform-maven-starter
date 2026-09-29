package com.htv.smartfarm.identity.config;

import com.htv.smartfarm.identity.grpc.security.GrpcRequestSecurity;
import com.htv.smartfarm.proto.identity.v1.IdentityDirectoryServiceGrpc;
import com.htv.smartfarm.security.grpc.GrpcMethodPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityGrpcSecurityConfiguration {

    @Bean
    GrpcMethodPolicy identityGrpcMethodPolicy() {
        return GrpcMethodPolicy.builder()
                .requireAuthority(
                        IdentityDirectoryServiceGrpc
                                .getGetPrincipalMethod()
                                .getFullMethodName(),
                        GrpcRequestSecurity.PRINCIPAL_READ
                )
                .requireAuthority(
                        IdentityDirectoryServiceGrpc
                                .getUpdatePrincipalProfileMethod()
                                .getFullMethodName(),
                        GrpcRequestSecurity.PRINCIPAL_UPDATE
                )
                .requireAuthority(
                        IdentityDirectoryServiceGrpc
                                .getCheckPermissionMethod()
                                .getFullMethodName(),
                        GrpcRequestSecurity.PERMISSION_CHECK
                )
                .requireAuthority(
                        IdentityDirectoryServiceGrpc
                                .getBatchCheckPermissionsMethod()
                                .getFullMethodName(),
                        GrpcRequestSecurity.PERMISSION_CHECK
                )
                .requireAuthenticated(
                        IdentityDirectoryServiceGrpc
                                .getPingMethod()
                                .getFullMethodName()
                )
                .build();
    }
}