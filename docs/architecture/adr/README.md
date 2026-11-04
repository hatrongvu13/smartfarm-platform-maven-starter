# Architecture Decision Records

Mẫu: Bối cảnh → Quyết định → Hệ quả → Bằng chứng. Trạng thái: **Đã chốt**, **Đề xuất**, **Thay thế bởi ...**.

| ADR | Tiêu đề | Trạng thái |
|---|---|---|
| [0001](0001-bff-server-side-tokens.md) | BFF giữ token phía server | Đã chốt |
| [0002](0002-spring-authorization-server.md) | Spring Authorization Server làm OIDC provider | Đã chốt |
| [0003](0003-mtls-ssl-bundles-spiffe.md) | mTLS bằng Spring Boot SSL bundles + SAN SPIFFE | Đã chốt |
| [0004](0004-local-jwt-validation-cached-jwks.md) | Resource server tự kiểm JWT bằng JWKS cache | Đã chốt |
| [0005](0005-database-per-service.md) | Database per service | Đã chốt (user riêng: M4) |
| [0006](0006-per-service-audience.md) | Audience riêng cho từng resource server | Đề xuất |
| [0007](0007-hermetic-gateway-tests.md) | Test gateway dùng kho session trong bộ nhớ | Đã chốt (M2a) |
| [0008](0008-gateway-management-port.md) | Actuator gateway trên cổng quản trị loopback | Đã chốt (M2) |
| [0009](0009-redis-fail-closed.md) | Redis lỗi thì gateway fail-closed với 503 | Đã chốt (M3) |
| [0010](0010-security-headers.md) | CSP theo profile và HSTS sau edge TLS | Đã chốt (M5b) |
