1. Luồng Cấp Phát Token & Xác Thực (TokenGrantFacade & RefreshSession)
   Luồng hiện trạng xử lý việc cấp phát token (Access/Refresh Token) qua các grant type và quản lý phiên.

Đoạn mã
```mermaid
sequenceDiagram
autonumber
actor Client
participant TokenGrantFacade as TokenGrantFacade
participant TokenGrant as TokenGrant (Strategy)
participant RefreshSessionPort as RefreshSessionPort
participant TokenSigner as TokenSigner
participant Audit as LoggingTokenGrantAudit

    Client->>TokenGrantFacade: Yêu cầu cấp token (GrantType, Credentials/Token)
    TokenGrantFacade->>TokenGrant: Định tuyến theo GrantType
    
    alt Grant Type: Password / Custom
        TokenGrant->>TokenSigner: Xác thực & Ký IssuedToken
        TokenSigner-->>TokenGrant: Trả về IssuedToken
    else Grant Type: Refresh Token
        TokenGrant->>RefreshSessionPort: Kiểm tra / Thu hồi RefreshSessionEntity
        RefreshSessionPort-->>TokenGrant: Xác thực thành công & tạo RefreshSessionDescriptor
        TokenGrant->>TokenSigner: Ký token mới
        TokenSigner-->>TokenGrant: Trả về IssuedToken
    end

    TokenGrant->>Audit: Ghi log kiểm toán (TokenGrantAudit)
    TokenGrant-->>TokenGrantFacade: Trả về IssuedToken
    TokenGrantFacade-->>Client: Phản hồi Token thành công
```
🔍 Gợi ý cải thiện & Hoàn thiện:

Kiểm tra hiện trạng: Đảm bảo TokenGrantFacade tách biệt hoàn toàn logic kiểm tra lỗi (Exception Handling) cho từng Grant Type, tránh việc dồn quá nhiều logic vào một class.

Cải thiện: Thêm cơ chế Token Revocation Blacklist hoặc kiểm tra Session State nghiêm ngặt hơn tại RefreshSessionAdministrationService để phòng chống tấn công Replay Attack (đã có test RefreshReplayRevocationServiceTest, cần đảm bảo implement đúng chuẩn).

2. Luồng Xác Thực Đa Yếu Tố (MfaChallengeService)
   Luồng hiện trạng xử lý quy trình thử thách MFA (Multi-Factor Authentication) khi người dùng thực hiện các thao tác nhạy cảm hoặc đăng nhập.

Đoạn mã
```mermaid
sequenceDiagram
autonumber
actor User
participant MfaService as MfaChallengeService
participant ChallengeRepo as MfaChallengeRepository
participant AuthenticatorRepo as UserAuthenticatorRepository
participant CredentialAdmin as CredentialAdministrationService
participant Digest as DigestSupport

    User->>MfaService: Yêu cầu tạo thách thức MFA (MfaChallengePurpose)
    MfaService->>AuthenticatorRepo: Lấy danh sách thiết bị/phương thức xác thực của User
    AuthenticatorRepo-->>MfaService: Trả về UserAuthenticatorEntity
    
    MfaService->>MfaService: Lựa chọn trình xác thực (UserAuthenticatorSelection)
    MfaService->>ChallengeRepo: Lưu bản ghi MfaChallengeEntity (Trạng thái Pending)
    MfaService-->>User: Trả về MfaChallenge (Mã thách thức / Thôn tin OTP)

    User->>MfaService: Gửi mã xác thực phản hồi (Challenge Response)
    MfaService->>ChallengeRepo: Tìm & xác thực MfaChallengeEntity theo ID
    MfaService->>Digest: Băm/Giải mã kiểm tra tính hợp lệ qua DigestSupport
    Digest-->>MfaService: Kết quả khớp
    
    MfaService->>CredentialAdmin: Cập nhật trạng thái AuthenticatorStatus (nếu thành công)
    MfaService-->>User: Xác thực MFA thành công
```
🔍 Gợi ý cải thiện & Hoàn thiện:

Kiểm tra hiện trạng: MfaChallengeService đang gánh khá nhiều trách nhiệm (God class nhỏ: vừa chọn authenticator, vừa tạo challenge, vừa verify).

Cải thiện: Nên tách nhỏ UserAuthenticatorSelection thành một chiến lược (Strategy) riêng biệt để dễ dàng thêm các loại MFA mới (như WebAuthn, Biometric, Email OTP) mà không sửa đổi file MfaChallengeService quá lớn.

3. Luồng Đồng Bộ Sự Kiện Bất ĐỒng Bộ (IdentityOutboxRelay)
   Luồng hiện trạng đảm bảo tính nhất quán dữ liệu (Transactional Outbox Pattern) kết hợp với MQTT kết nối thiết bị.

Đoạn mã
```mermaid
sequenceDiagram
autonumber
participant App as Ứng dụng / TransactionService
participant OutboxEntity as IdentityOutboxEntity
participant OutboxRelay as IdentityOutboxRelay
participant MqttManager as IdentityMqttConnectionManager
participant InboxEntity as IdentityMqttInboxEntity

    App->>OutboxEntity: Lưu sự kiện Domain (Outbox Pattern trong cùng DB Transaction)
    Note over App,OutboxEntity: Đảm bảo không bị mất sự kiện khi DB commit thành công

    loop Định kỳ / Scheduled Task
        OutboxRelay->>OutboxEntity: Quét các message chưa gửi (Unpublished)
        OutboxEntity-->>OutboxRelay: Trả về danh sách IdentityOutboxMessage
        
        OutboxRelay->>MqttManager: Gửi message qua MQTT Broker
        MqttManager-->>OutboxRelay: Trạng thái kết nối (State / Snapshot)
        
        alt Gửi thành công
            OutboxRelay->>OutboxEntity: Đánh dấu message là Đã gửi (Processed)
        else Gửi thất bại
            OutboxRelay->>OutboxEntity: Tăng số lần thử lại (Retry count / Backoff)
        end
    end

    Note over MqttManager,InboxEntity: Chiều nhận tin (Inbox Pattern)
    MqttManager->>InboxEntity: Nhận lệnh MQTT đến -> Lưu vào IdentityMqttInboxEntity
    InboxEntity->>App: Xử lý lệnh qua IdentityMqttInboxMaintenance
```
🔍 Gợi ý cải thiện & Hoàn thiện:

Kiểm tra hiện trạng: Cần xem lại cơ chế Locking (như PESSIMISTIC_WRITE hoặc @Lock) trong IdentityOutboxRelay để tránh trường hợp nhiều instance của ứng dụng quét trùng lặp Outbox Message khi scale microservices/instances.

Cải thiện: Bổ sung cơ chế Dead Letter Queue (DLQ) hoặc bảng log lỗi riêng cho các message outbox bị lỗi quá số lần cho phép để tiện giám sát (Monitoring).

4. Luồng Quản Trị Tenant & Phân Quyền (TenantMembershipService)
   Luồng quản lý thành viên thuộc Tenant và phân quyền gắn với Farm (MembershipFarmEntity).

Đoạn mã
```mermaid
sequenceDiagram
autonumber
actor Admin
participant TenantAdminSvc as DefaultTenantIdentityAdministrationService
participant MembershipSvc as TenantMembershipService
participant TenantEntity as TenantEntity
participant MembershipEntity as TenantMembershipEntity
participant FarmEntity as MembershipFarmEntity

    Admin->>TenantAdminSvc: Yêu cầu thêm User vào Tenant / Farm
    TenantAdminSvc->>TenantEntity: Kiểm tra Tenant tồn tại & Active
    TenantEntity-->>TenantAdminSvc: Tenant hợp lệ

    TenantAdminSvc->>MembershipSvc: Gọi dịch vụ phân quyền thành viên
    MembershipSvc->>MembershipEntity: Tạo/Cập nhật TenantMembershipEntity
    MembershipSvc->>FarmEntity: Liên kết MembershipFarmId với Farm tương ứng

    FarmEntity-->>MembershipSvc: Lưu thành công liên kết quyền Farm
    MembershipSvc-->>TenantAdminSvc: Trả về TenantMembershipData
    TenantAdminSvc-->>Admin: Phản hồi phân quyền thành công
```
🔍 Gợi ý cải thiện & Hoàn thiện:

Kiểm tra hiện trạng: Đảm bảo các ràng buộc Unique Constraint giữa TenantMembershipEntity và MembershipFarmEntity được định nghĩa rõ ràng ở tầng Database Entity để tránh tạo trùng lặp quyền truy cập.

Cải thiện: Tách biệt rõ ràng giữa Tenant-Level Permission (Cấp độ công ty/tổ chức) và Farm-Level Permission (Cấp độ trang trại thực tế) trong các DTO phản hồi để UI dễ hiển thị.