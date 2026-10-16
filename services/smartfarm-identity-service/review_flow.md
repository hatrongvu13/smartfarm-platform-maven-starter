# Review Luồng Nghiệp Vụ — Identity Service

> ⚠️ **ĐÃ HỢP NHẤT VỀ MASTER CHECKLIST (2026-10-16):**  
> Tài liệu này chứa sơ đồ giải thích các luồng nội bộ (TokenGrantFacade, MFA Challenge, Outbox Relay, Tenant Membership). Toàn bộ kế hoạch cải tiến, tính năng đã xong và các đề xuất mở rộng đã được chuẩn hóa và hợp nhất vào [**Master Checklist Identity Service**](../../IDENTITY_BACKLOG_UPDATED.md).

---

## 1. Luồng Cấp Phát Token & Xác Thực (TokenGrantFacade & RefreshSession)
Luồng hiện trạng xử lý việc cấp phát token (Access/Refresh Token) qua các grant type và quản lý phiên.

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
    
    alt Grant Type: Password / Service / Async
        TokenGrant->>TokenSigner: Xác thực & Ký IssuedToken (RS256)
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

- **Đã hoàn thành:** TokenGrantFacade tách biệt hoàn toàn logic kiểm tra lỗi cho từng Grant Type, tích hợp `TokenContext` hỗ trợ cờ `is_async = true`.
- **Đã hoàn thành:** Cơ chế chống Replay Attack (`REFRESH_TOKEN_REUSE`) thu hồi toàn bộ session family trong transaction độc lập `REQUIRES_NEW`.

---

## 2. Luồng Xác Thực Đa Yếu Tố (MfaChallengeService)
Luồng hiện trạng xử lý quy trình thử thách MFA (Multi-Factor Authentication) khi người dùng thực hiện các thao tác nhạy cảm hoặc đăng nhập tài khoản đặc quyền.

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
    MfaService-->>User: Trả về MfaChallenge (Mã thách thức / Thông tin OTP)

    User->>MfaService: Gửi mã xác thực phản hồi (Challenge Response)
    MfaService->>ChallengeRepo: Tìm & xác thực MfaChallengeEntity theo ID
    MfaService->>Digest: Băm/Giải mã kiểm tra tính hợp lệ qua DigestSupport
    Digest-->>MfaService: Kết quả khớp
    
    MfaService->>CredentialAdmin: Cập nhật trạng thái AuthenticatorStatus (nếu thành công)
    MfaService-->>User: Xác thực MFA thành công
```

- **Đã hoàn thành:** Luồng TOTP độc lập bảo vệ bằng Challenge Token; mã hóa secret bằng AES-256 (`MfaEncryptionKeyProvider`); dọn dẹp định kỳ expired challenge.
- **Quyết định kiến trúc:** Không tích hợp WebAuthn / Biometric phức tạp để tránh over-engineering và phình to codebase.

---

## 3. Luồng Đồng Bộ Sự Kiện Bất Đồng Bộ (IdentityOutboxRelay & MQTT)
Luồng hiện trạng đảm bảo tính nhất quán dữ liệu (Transactional Outbox Pattern) kết hợp với MQTT.

```mermaid
sequenceDiagram
autonumber
participant App as Ứng dụng / TransactionService
participant OutboxEntity as IdentityOutboxEntity
participant OutboxRelay as IdentityOutboxRelay
participant MqttManager as IdentityMqttConnectionManager

    App->>OutboxEntity: Lưu sự kiện Domain / Invalidation (Outbox Pattern trong cùng DB Transaction)
    Note over App,OutboxEntity: Đảm bảo không bị mất sự kiện khi DB commit thành công

    loop Định kỳ / Scheduled Task
        OutboxRelay->>OutboxEntity: Quét các message chưa gửi (Unpublished)
        OutboxEntity-->>OutboxRelay: Trả về danh sách IdentityOutboxMessage
        
        OutboxRelay->>MqttManager: Gửi message qua MQTT Broker
        
        alt Gửi thành công
            OutboxRelay->>OutboxEntity: Đánh dấu message là Đã gửi (Processed)
        else Gửi thất bại
            OutboxRelay->>OutboxEntity: Tăng số lần thử lại (Retry count / Backoff)
        end
    end
```

- **Đã hoàn thành:** Xuất bản `DomainEvent` Protobuf (Lifecycle & Invalidation) lên MQTT topic `events/identity/authorization-invalidated`.

---

## 4. Luồng Quản Trị Tenant & Phân Quyền (TenantMembershipService)
Luồng quản lý thành viên thuộc Tenant và liên kết phạm vi nông trại.

```mermaid
sequenceDiagram
autonumber
actor Admin
participant TenantAdminSvc as DefaultTenantIdentityAdministrationService
participant MembershipSvc as TenantMembershipService
participant TenantEntity as TenantEntity
participant MembershipEntity as TenantMembershipEntity

    Admin->>TenantAdminSvc: Yêu cầu thêm User vào Tenant
    TenantAdminSvc->>TenantEntity: Kiểm tra Tenant tồn tại & Active
    TenantEntity-->>TenantAdminSvc: Tenant hợp lệ

    TenantAdminSvc->>MembershipSvc: Gọi dịch vụ phân quyền thành viên
    MembershipSvc->>MembershipEntity: Tạo/Cập nhật TenantMembershipEntity
    MembershipEntity-->>MembershipSvc: Lưu thành công
    MembershipSvc-->>TenantAdminSvc: Trả về TenantMembershipData
    TenantAdminSvc-->>Admin: Phản hồi phân quyền thành công
```

- **Đã hoàn thành:** Phân tách rõ ràng giữa Account toàn cục, Profile và Tenant Membership; phân quyền toàn cục tuân thủ ADR-001.
