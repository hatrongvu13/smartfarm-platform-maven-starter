# Tích hợp service module với Identity và `smartfarm-security`

> Reconciled 2026-10-20 against `libs/smartfarm-security`, Identity OAuth/OIDC work in progress, and current service modules. Đây là hướng dẫn theo hiện trạng, không giả định các migration chưa hoàn tất.

## Mục tiêu và kết luận

Mục tiêu là để mỗi module nghiệp vụ chỉ cần thêm dependency dùng chung, khai báo định danh/audience và map quyền nghiệp vụ; không tự viết JWT parsing, signature/expiry validation, Identity permission catalog hoặc token transport. Phần xử lý tenant data và quyết định nghiệp vụ vẫn thuộc module sở hữu dữ liệu.

**Đã triển khai thêm một bước giảm lặp:** method policy có thể khai báo bằng `smartfarm.security.grpc.authorities` thay vì một Java `@Configuration` riêng; `require-explicit-policy: true` bật kiểm tra startup rằng mọi method của các `BindableService` đã đăng ký đều có đúng một phân loại và không còn policy trỏ tới RPC đã xóa. Tên RPC phải đặt trong YAML bracket notation (`"[package.Service/Method]"`) để Spring giữ nguyên dấu chấm và dấu gạch chéo của map key. Sáu service đã chuyển mapping hiện có sang YAML và bật coverage gate.

**Hiện trạng chưa đạt “chỉ thêm common là xong”.** Với gRPC server, thêm `smartfarm-security` cùng cấu hình issuer/JWKS/audience sẽ bật interceptor xác thực global khi ứng dụng có gRPC `BindableService`. Nhưng:

- Mỗi module vẫn phải khai báo scope tối thiểu theo từng full gRPC method; `require-explicit-policy` buộc phân loại đầy đủ. Chỉ bật gate này mới ngăn việc thêm RPC mới âm thầm rơi về authentication-only.
- HTTP resource security có auto-configuration opt-in `smartfarm.security.http.enabled=true` cho Servlet hoặc Reactive. Gateway và Identity giữ security chains riêng; default HTTP chain chỉ cho health và Identity edge routes cần thiết, còn endpoints khác authenticated-only. Service có HTTP authorization/CSRF/session policy phức tạp vẫn cần khai báo riêng.
- `BearerCallCredentials` chỉ chuyển token vào metadata; nó không gọi Identity, provision client, cache/refresh token hoặc tự biết audience, tenant và actor. Order, Reporting và Gateway hiện có các `ServiceTokenClient` riêng; caller và Saga executor vẫn gắn credentials thủ công.
- `GrpcSecurityContext` cung cấp identity/tenant/actor nhưng service vẫn phải dùng chúng trong application/domain query và kiểm tra tenant isolation. Interceptor không thể thay thế row-level/domain authorization.
- Authorization Server có `client_credentials`, nhưng clients mặc định rỗng; downstream modules vẫn dùng custom gRPC token issuance. RFC 8693 và caller migration chưa hoàn tất.

**Đánh giá hiện tại:** khoảng **7/10 cho tái sử dụng security common ở gRPC resource-server**, và **chưa product-ready cho OAuth client integration**. Điểm tăng do policy mapping và RPC coverage đã được common nạp/kiểm tra, không phải do security tự suy ra quyền nghiệp vụ. Outbound token lifecycle, HTTP integration và end-to-end migration vẫn còn.

## Sơ đồ request path và cấu trúc triển khai

```mermaid
sequenceDiagram
    autonumber
    participant Caller as Gateway / worker / service
    participant Token as Identity OAuth AS (target)
    participant Interceptor as gRPC JWT interceptor
    participant Policy as Method policy (service YAML)
    participant Handler as Resource service handler
    participant DB as Tenant-scoped repository

    opt Workload chưa có cached access token (client provider common còn là backlog)
        Caller->>Token: client_credentials (audience + least-privilege scopes)
        Token-->>Caller: access token (iss, aud, exp, scope, client_id)
    end
    Caller->>Interceptor: RPC + bearer, tenant, correlation metadata
    Interceptor->>Interceptor: Verify signature, issuer, exp, aud, token profile
    Interceptor->>Policy: Resolve exact full RPC name
    Policy->>Policy: Require configured authority; reject if absent
    Interceptor->>Handler: Attach verified SecurityIdentity to gRPC Context
    Handler->>DB: Query/command constrained by verified tenant and ownership
    DB-->>Handler: Tenant-authorized result
    Handler-->>Caller: Result
```

```text
libs/smartfarm-security/
  grpc/
    GrpcMethodPolicy.java                 # exact method -> authority / public / authenticated
    GrpcMethodPolicyProperties.java       # YAML binding + authority normalization
    GrpcMethodPolicyCoverage.java         # startup validation against registered BindableService
    JwtServerInterceptor.java             # authentication + static/dynamic policy enforcement
    BearerCallCredentials.java            # attaches already-obtained bearer token
  jwt/
    SmartFarmJwtValidator.java            # issuer, signature, expiry, audience and claims
    JwtAuthorities.java                   # canonical scope/role conversion
  authorization/
    AuthorizationVersionWatermarkStore.java
    AuthorizationInvalidationMqttConsumer.java
  web/
    ServletResourceSecurity.java           # imported; opt-in via smartfarm.security.http.enabled
    ReactiveResourceSecurity.java          # imported; opt-in via smartfarm.security.http.enabled
```

Identity issues credentials and owns the global permission catalog. Each service owns one exact RPC-to-scope mapping and its data-ownership rules. The common library validates/applies these contracts; it does not infer whether an operation is read or write by guessing from method names.

## Phân định trách nhiệm

| Concern | Phần dùng chung (`smartfarm-security`) | Phần Identity | Phần service module |
|---|---|---|---|
| JWT authenticity | Verify issuer, JWKS signature, expiry, audience, `sub`, token type, claim-contract version; user token cần tenant | Phát token theo grant, audience, scope và profile | Chỉ cấu hình đúng issuer/JWKS/audience; không viết JWT verifier riêng |
| gRPC inbound | Global interceptor; gRPC health public, các method khác authenticated-by-default; freshness check; tenant header phải khớp token; correlation/audit hooks | Định nghĩa authority catalog và client grant policy | Map mỗi RPC method sang authority tối thiểu; đọc principal/tenant từ security context |
| HTTP inbound | Servlet/reactive resource security auto-config opt-in; disabled unless explicitly enabled | Phát access token | Bật config cho resource server đơn giản; route/CSRF/session policy riêng nếu HTTP surface phức tạp |
| OAuth client identity | Chưa có Identity OAuth token client/cache abstraction | Provision client id/secret, grants, scope, một audience | Cấu hình machine identity và yêu cầu scope tối thiểu; không giữ secret trong source |
| Outbound gRPC token | `BearerCallCredentials` gắn bearer + tenant/correlation metadata | Cấp OAuth access token; hiện legacy custom gRPC issuer vẫn đang được callers dùng | Hiện caller tự lấy/cache token, chọn audience/tenant/actor và gắn credentials |
| Tenant/data authorization | Kiểm tra token tenant và optional `x-tenant-id` match | Membership/authorization policy và claims | Bắt buộc giới hạn truy vấn/command theo tenant từ token; kiểm tra farm/resource ownership |
| Runtime invalidation | In-memory watermark store; MQTT consumer có thể bật bằng cấu hình | Publish signed authorization-invalidated event | Cấu hình broker/topic/credentials phù hợp; đánh giá fail-open/outage semantics theo môi trường |

Identity sở hữu permission catalog và quyết định ai được cấp scope. Resource service sở hữu mapping `RPC method -> required scope` và data-level tenant/ownership checks. `smartfarm-security` xử lý protocol validation, không được duy trì catalog quyền thứ hai.

## Tích hợp hiện hành cho gRPC resource server

### 1. Thêm dependency

Các service Order, Inventory, Finance, Health, Livestock và Reporting đã khai báo `smartfarm-security`. Service mới khai báo module này cùng dependency gRPC/Spring Boot tương ứng; không thêm JWT parser hoặc decoder riêng nếu không có lý do tương thích được ghi lại.

### 2. Khai báo định danh và trust boundary

```yaml
spring:
  application:
    name: smartfarm-inventory-service

smartfarm:
  security:
    jwt:
      issuer: ${SMARTFARM_JWT_ISSUER}
      jwk-set-uri: ${SMARTFARM_JWKS_URI}
      audiences:
        - ${SMARTFARM_JWT_AUDIENCE:smartfarm-inventory}
      allow-local-http: ${SMARTFARM_SECURITY_ALLOW_LOCAL_HTTP:false}
    grpc:
      enabled: true
      audit-enabled: true
      correlation-enabled: true
      require-explicit-policy: true
      authorities:
        "[smartfarm.inventory.v1.InventoryService/GetItem]": inventory:read
        "[smartfarm.inventory.v1.InventoryService/ReceiveStock]": inventory:write
    http:
      enabled: false
```

`audiences` là các resource audience mà service này chấp nhận; không dùng audience của service khác làm mặc định. Production issuer/JWKS phải dùng HTTPS. `allow-local-http` chỉ dành cho môi trường local được cô lập.

Khi service có Spring gRPC server `BindableService`, `SmartFarmGrpcSecurityAutoConfiguration` tự đăng ký global JWT, correlation và audit interceptors. YAML `authorities` được chuyển thành exact method policy. Bật `require-explicit-policy: true` để startup fail nếu method chưa được phân loại hoặc policy trỏ đến RPC không tồn tại. Nếu gate bị tắt và không có custom policy, fallback chỉ yêu cầu authentication (ngoại trừ gRPC health); đây **không** phải policy đủ dùng cho production.

### 3. Map scope cho từng RPC trong service

Khai báo policy trong `application.yml`, không cần Java `@Configuration` cho mapping:

```yaml
smartfarm:
  security:
    grpc:
      require-explicit-policy: true
      authorities:
        # Abbreviated example only. Production must classify every registered RPC.
        "[smartfarm.inventory.v1.InventoryService/GetStockBalance]": inventory:read
        "[smartfarm.inventory.v1.InventoryService/ReceiveStock]": inventory:write
```

Khai báo đủ mọi RPC, gồm operation admin/Saga; không bỏ sót method. Coverage gate kiểm tra chính xác những service methods mà Spring gRPC đăng ký. Chỉ khai báo health method public nếu chủ động thay đổi mặc định; không permit-all business methods. `authenticated-methods` chỉ dành cho RPC mà thiết kế thật sự không có scope cụ thể. Tên scope phải khớp scope được Identity cấp; Identity sở hữu permission catalog.

`OFF`/`SHADOW`/`ENFORCE` của dynamic authorization không thay cho static method policy: static authority check vẫn được áp dụng. Không bật dynamic `ENFORCE` chỉ bằng cấu hình nếu service chưa có evaluator hợp lệ và rollout/rollback đã được kiểm chứng.

### 4. Đọc authenticated identity và tenant

Trong gRPC handler/application boundary, dùng `GrpcSecurityContext` (ví dụ `requireTenant()` / `requireEffectiveActor()`); không lấy tenant làm căn cứ tin cậy từ request payload hoặc header do caller tự đặt. Interceptor từ chối `x-tenant-id` khác tenant trong JWT, nhưng không tự sửa mọi repository query. Mọi đọc/ghi phải mang tenant đến persistence query và kiểm tra ownership của farm/resource.

### 5. Inbound test gate tối thiểu

Mỗi module cần test method policy và server interceptor với các trường hợp: thiếu/sai bearer → `UNAUTHENTICATED`; sai issuer/signature/audience, hết hạn/thiếu `exp`, token type/contract version không hợp lệ → bị từ chối; tenant header mismatch → `PERMISSION_DENIED`; token hợp lệ nhưng thiếu method scope → `PERMISSION_DENIED`; đủ scope + tenant hợp lệ → request đến handler; repository query không thể vượt tenant boundary.

## Outbound service-to-service: trạng thái và yêu cầu

`BearerCallCredentials` nhận `Supplier<String>` và tự thêm `Authorization: Bearer`, `x-tenant-id` (nếu có) cùng correlation id. Nó **không** là OAuth client. Với mô hình OAuth mục tiêu, mỗi workload cần confidential client có đúng một resource audience và least-privilege scopes; secrets phải inject từ deployment secret store. Saga/worker xin token mới khi retry; không sử dụng JWT không có hạn hết.

Hiện Gateway, Order và Reporting có các `ServiceTokenClient`/token call logic riêng, đang gọi custom Identity gRPC. Những lớp này xử lý các policy riêng (delegation của Gateway, tenant/actor cache key, token cache) nên không thể thay bằng `BearerCallCredentials` đơn lẻ. Không khai báo migration OAuth đã hoàn thành chỉ vì `client_credentials` endpoint có mặt.

## Mức sẵn sàng theo module

| Module | Common dependency / inbound auth | Method-level permission | Outbound Identity/client state | Đánh giá tích hợp |
|---|---|---|---|---|
| Inventory | Có; global gRPC interceptor và JWT properties | YAML authorities + startup coverage gate | Không cấp token tới Identity trực tiếp; caller Gateway/Order cấp bearer | Policy coverage được check khi app start; cần tenant data checks |
| Finance | Có; global gRPC interceptor | YAML authorities + startup coverage gate | Caller Gateway/Order cấp bearer | Policy coverage được check khi app start; cần tenant tests |
| Order | Có; global gRPC interceptor | YAML authorities + startup coverage gate, bao gồm Saga admin RPC | Có `ServiceTokenClient` custom gRPC cho worker | Chưa đạt OAuth client migration; class token client cần thay bằng shared OAuth client |
| Reporting | Có; global gRPC interceptor | YAML authorities + startup coverage gate | Có `ServiceTokenClient` custom gRPC | Chưa đạt OAuth client migration; trùng logic lấy/cache token với Order |
| Livestock / Health | Có dependency, JWT properties và global interceptor | YAML authorities + startup coverage gate; Livestock `Ping` được phân loại authenticated-only | Thường là resource-server callers; không có shared token lifecycle | Policy coverage được check khi app start; cần HTTP/data isolation tests |
| Gateway | Có JWT resource-server configuration riêng, không phải consumer-only template | Route/GraphQL policy do Gateway sở hữu | Có delegated `ServiceTokenClient` custom gRPC, gắn actor token và watermark | Cần BFF/OIDC migration; delegated flow không thể chuyển thành client credentials thuần |

Ma trận này phản ánh tích hợp source-level, không chứng minh được database-backed authorization, live MQTT invalidation hoặc deployed OAuth clients. Mỗi deployment cần chạy test tương ứng theo môi trường.

## Đích đề xuất: module consumer chỉ khai báo identity và quyền

Để gần mục tiêu “thêm common + config”, cần hoàn thiện `smartfarm-security` thành starter/API integration thống nhất theo các phần sau:

1. **Inbound resource-server starter:** servlet/reactive security auto-config đã được import và chỉ bật theo opt-in property; cần thêm route-to-authority config và kiểm chứng rõ Gateway custom CSRF/session rules không bị ghi đè.
2. **Khai báo gRPC policy gọn, fail-closed:** YAML `full-method-name -> required authority`, authenticated-only/public classifications và `require-explicit-policy` startup coverage gate đã triển khai. Sinh policy từ proto là cải tiến sau này; health là public duy nhất theo mặc định.
3. **Shared OAuth machine client:** client-credentials token provider cho Identity với audience/scope configuration, credential injection, single-flight refresh/cache theo issuer-client-audience-scope, expiry skew, bounded retry/deadline, lỗi fail-closed và không log secret/token. Không dùng client-credentials cho user delegation.
4. **Common caller interceptor/factory:** gắn access token và correlation metadata tự động cho blocking/async gRPC stubs; tenant chỉ truyền từ verified request context hoặc workload policy, không suy ra từ arbitrary payload.
5. **Identity provisioning contract:** một schema/configured client definition có client id, secret reference, audience/resource, scopes và grant types; validate đồng bộ giữa client registration, Identity permission catalog và downstream method policy. Giữ secret rotation vận hành ngoài source.
6. **Tenant context contract:** chuẩn hóa accessors/propagation từ verified JWT đến application services; dữ liệu vẫn enforce tenant isolation tại query/command và authorization domain của service.
7. **Starter test kit:** reusable test support cho invalid JWT, missing scope, method coverage, tenant mismatch và client token refresh; từng service chỉ thêm test về policy mapping/data isolation riêng.

Sau các phần này, service resource module mới chủ yếu cần:

```text
1. dependency `smartfarm-security`
2. issuer + JWKS + expected audience
3. map RPC method -> Identity scope/authority
4. outbound client id/secret reference + audience/scopes (nếu gọi service khác; OAuth token client dùng chung còn là hạng mục kế tiếp)
5. tenant-aware business queries/tests
```

Không thể loại bỏ bước 3 hoặc 5 bằng một thư viện dùng chung: quyền trên từng operation và quyền sở hữu dữ liệu là nghiệp vụ của service. Đối với Gateway/BFF và user delegation cần thêm OAuth callback/session/token-exchange policy; không coi đó là cấu hình nhỏ của resource server.

## Release decision

- **Cho phép phát triển resource service theo baseline hiện hành:** có, nếu mọi gRPC method map scope rõ ràng, issuer/audience đúng, tenant-scoped query và negative authorization tests pass.
- **Tuyên bố tích hợp OAuth end-to-end:** chưa; còn clients legacy gRPC, client provisioning mặc định rỗng, RFC 8693, Gateway BFF/Web session và PostgreSQL/OIDC release gates.
- **Service mới không cần tự viết JWT security:** đúng đối với gRPC inbound khi auto-configuration được kích hoạt. Không đồng nghĩa “không cần khai báo policy, token client, tenant checks hoặc HTTP security”.

Tham chiếu: [Identity OAuth/OIDC target](../architecture/identity-oauth2-oidc-target.md), [security architecture](security-architecture.md), [configuration](../operations/configuration.md), [gRPC API](../api/grpc-api.md).
