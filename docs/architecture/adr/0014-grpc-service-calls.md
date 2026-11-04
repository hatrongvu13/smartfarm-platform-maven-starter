# ADR-0014: gRPC giữa service: mTLS + SAN + JWT người dùng

**Trạng thái:** Đã chốt (D3-B, P2, 2026-11-04). Client phía farm là P3.

**Bối cảnh.** Farm cần hỏi identity "người này có phải thành viên của tổ chức không" (gán người phụ trách). Hợp đồng nằm ở `libs/farmverse-contracts` (`farmverse.identity.v1.IdentityDirectory`). Khi identity trả lời, nó cần biết ba điều: máy nào đang gọi, service nào, và gọi thay cho tenant nào.

| Phương án tenant (D3) | Ưu | Nhược |
|---|---|---|
| A. mTLS, tin `tenant_id` farm gửi | Đơn giản, không cần token | Farm bị chiếm thì hỏi được mọi tenant |
| **B. Chuyển tiếp access token của người dùng** | Tenant do token đã ký quyết định; farm bị chiếm chỉ hỏi được tenant của người đang có token hợp lệ | Token đi thêm một chặng; gắn với audience của token |
| C. Token `client_credentials` của farm | Token máy chuẩn | Token không có tenant, nên lại phải tin farm như A |

**Quyết định.** B, với ba lớp như REST:
1. **TLS** (profile `mtls`, prod luôn bật): `spring.grpc.server.ssl.bundle=farmverse`, `client-auth=REQUIRE`. Dùng cùng CA và cert với REST.
2. **Service nào**: `ServiceIdentityInterceptor` đọc SAN `spiffe://farmverse/<service>` (dùng chung `ServiceIdentityFilter.spiffeId`). Chỉ `farmverse.grpc.allowed-callers` (mặc định `farm`) gọi được `IdentityDirectory`. Sai thì trả `PERMISSION_DENIED` trước khi đọc token.
3. **Người dùng nào**: `GrpcSecurity.oauth2ResourceServer().jwt()` dùng cùng bean `JwtDecoder` với REST: chữ ký, iss, hạn, aud `identity-service`. Rpc đòi `SCOPE_farmverse.api`. Service còn đòi `token_type=user` và lấy tenant từ claim `tenant_id`. `tenant_id` trong request chỉ để đối chiếu: khác token thì `PERMISSION_DENIED`. Người ở tenant khác, hay id không tồn tại, đều trả `NOT_FOUND`.

Thêm:
- Cổng 9093, bind loopback. Mặc định của Spring gRPC là 9090, trùng cổng quản trị trong container.
- Health gRPC mở cho mọi kết nối đã qua TLS.
- Reflection chỉ bật ở dev.
- `GrpcProductionGuard` dừng identity khi ở prod mà thiếu bundle, thiếu `REQUIRE`, hoặc reflection đang bật.
- Không log id hay tên người dùng.

**Bằng chứng.**
- [`IdentityDirectoryGrpcTest`](../../../services/identity-service/src/test/java/com/htv/farmverse/identity/grpc/IdentityDirectoryGrpcTest.java) (8 test, Netty thật, token ký bằng khóa thật):
  - ACTIVE và SUSPENDED trả đúng; khác tenant và id lạ → `NOT_FOUND`; `tenant_id` lệch token → `PERMISSION_DENIED`.
  - Không token, token giả, token hết hạn, aud chỉ có `farm-service` → `UNAUTHENTICATED`.
  - Thiếu scope, token máy → `PERMISSION_DENIED`.
  - Id sai dạng, lô quá 100 → `INVALID_ARGUMENT`.
  - Log không có tên người dùng hay token.
- [`IdentityGrpcMtlsTest`](../../../services/identity-service/src/test/java/com/htv/farmverse/identity/grpc/IdentityGrpcMtlsTest.java) (cert của `generate-dev-mtls.sh`):
  - Farm có token → OK.
  - Gateway hoặc identity, kể cả có token đúng → `PERMISSION_DENIED`.
  - Không cert hoặc plaintext → `UNAVAILABLE`.
  - Farm không token → `UNAUTHENTICATED`.
  - Health chạy với cert của service bất kỳ.
- [`GrpcConfigurationTest`](../../../services/identity-service/src/test/java/com/htv/farmverse/identity/grpc/GrpcConfigurationTest.java): mapper phủ mọi trạng thái; cấu hình prod thật (application + mtls + prod) qua được guard; guard chặn plaintext, thiếu `REQUIRE`, reflection bật.

**Chưa chứng minh.**
- Farm gọi thật (P3): deadline, retry, giảm cấp.
- Chạy trong container giữa hai pod.
- Khi làm ADR-0006 bước 2 (token riêng cho từng service), token farm nhận sẽ không còn `aud=identity-service`. Lúc đó farm cần token exchange (RFC 8693) để lấy token cho identity.

**Rollback.** `spring.grpc.server.enabled=false` (identity chạy như trước), hoặc xóa package `grpc` và dependency `spring-boot-starter-grpc-server`. Hợp đồng ở `libs/farmverse-contracts` để nguyên cũng không hại gì.
