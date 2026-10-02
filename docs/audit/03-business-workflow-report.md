# 03 — Business Workflow Report (Báo cáo quy trình nghiệp vụ)

> Mỗi workflow: actor, trigger, luồng chính/thay thế/thất bại, dữ liệu ghi, event phát, lớp hoàn thiện.

## A. Order lifecycle + Saga

### A1. PlaceOrder (đặt đơn nhiều dòng)
- **Actor**: user (qua gateway → `FarmOrderService/PlaceOrder`, cần `SCOPE_orders:write`).
- **Trigger**: gRPC `PlaceOrder` với `idempotency_key`, `farm_id`, ≥1 `OrderLine`.
- **Main flow** (FACT, `FarmOrderGrpcService.placeOrder`):
  1. Lấy `tenant` từ context đã xác thực; validate `idempotency_key`/`farm_id`/lines.
  2. Idempotent: nếu `(tenant, idempotencyKey)` đã có order → trả về order cũ (unique `uq_ord_idem`, V1).
  3. Validate từng line (qty>0, unitPrice>0, cùng currency), tính `totalMinor`.
  4. Lưu `OrderEntity` (status `ORDER_STATUS_CREATED`) + `OrderLineEntity`.
  5. `persistentSagas.create(tenant, orderId, actor, correlationId)` → tạo `OrderSagaEntity` + graph step (reserve×n → finance → commit×n → reverse-finance → release×n).
  6. `eventStore.appendBusiness(CREATED, ...)` (delivery-only, không vào archive).
- **Async advance**: `OrderSagaPersistentWorker.run` (poll 1s): `recoverStaleClaims` → `expireManualReviewsAndProcessingDeadlines` → `claimBatch` → `processOne`:
  - Forward: `nextForwardStepKey` → `claimStep` → `OrderSagaForwardStepExecutor.execute` (reserve/finance/commit, gRPC deadline 5s, token theo audience) → checkpoint succeeded/failed.
  - Fail: `markForwardStepFailed` (retry exp-backoff đến `maximum-attempts=5`; POST_FINANCE cạn attempt → `waitForFinanceManualReview`; khác → `beginCompensation`).
- **Alt flow**: Draft (`CreateDraftOrder/Update/Submit/Delete`) qua `OrderDraftService` (optimistic version).
- **Failure flow**: xem Compensation (A3).
- **Data writes**: `ord_order`, `ord_order_line`, `ord_saga`, `ord_saga_step`, `ord_outbox`, `ord_event_archive`.
- **Events**: `order.created.v1` (business), sau đó `order-changed.v1` (snapshot) tại mỗi transition qua `OrderEventStore.append`.
- **Completeness**: **COMPLETE** (đường worker). Lưu ý TECHNICAL_DEBT: `OrderSagaOrchestrator` inline tồn tại song song (ISSUE-03/08).

### A2. CancelOrder
- **Actor**: user (`CancelOrder`, `SCOPE_orders:write`).
- **Main flow** (FACT, `FarmOrderGrpcService.cancelOrder`): lock `findByTenantIdAndIdForUpdate`; chặn hủy khi `COMPLETED` (FAILED_PRECONDITION); idempotent khi đã `CANCELLED/CANCELLING/COMPENSATING`; `markCancelling` → `persistentSagas.requestCancellation` → `beginCompensationLocked(intent=CANCELLED)` → phát `order-changed` + `CANCEL_REQUESTED`.
- **Completeness**: **COMPLETE**.

### A3. Compensation (bù trừ)
- **Trigger**: forward step cạn attempt (non-finance) hoặc cancel.
- **Flow** (FACT, `OrderSagaTransactionService.beginCompensationLocked` + `OrderSagaCompensationStepExecutor`):
  - Tính `financePosted`, `committedLines`, `reservedLines`.
  - Nếu có line đã **COMMIT** → chuyển intent `MANUAL_REVIEW` + lý do "Partial inventory commit requires manual reconciliation" (tránh tự động đảo khi tồn kho đã trừ) — **điểm mạnh**.
  - Kích hoạt step compensation phù hợp (reverse-finance nếu financePosted; release cho line reserved-nhưng-chưa-commit).
  - Executor: `reverseFinance` (`originalExpenseKey = orderId + ":finance"`), `releaseStock`.
- **Completeness**: **COMPLETE** nhưng xem **ISSUE-03** (đường inline dùng key `:expense` lệch).

### A4. State machine
- `OrderDomainStatus.allowedNext()` (FACT) phủ: DRAFT→CREATED→STOCK_RESERVING→STOCK_RESERVED→(TASK_SCHEDULED)→FINANCE_POSTING→FINANCE_POSTED→COMPLETED, với các nhánh CANCELLING/COMPENSATING/FAILED/MANUAL_REVIEW. Terminal: COMPLETED, CANCELLED.
- Nhận xét: `TASK_SCHEDULED` có trong enum nhưng saga graph (reserve/finance/commit) **không có step tạo task** → trạng thái `TASK_SCHEDULED` dường như không được saga đi qua (INFERENCE — có thể dành cho luồng khác/tương lai). UNKNOWN.
- **Completeness**: **COMPLETE** cho vòng đời đặt-hàng cốt lõi; `TASK_SCHEDULED` = PARTIAL/UNKNOWN.

## B. Identity — Auth / RBAC / MFA

### B1. Login + MFA
- **Actor**: end-user (REST `POST /api/v1/auth/login` → `AuthService.login`). MFA: `/mfa/verify`, enrollment begin/confirm.
- **Flow** (FACT ở mức API, `AuthController` + `AuthService`): login → (nếu cần) challenge token → verify TOTP/recovery → cấp token qua `TokenService.issueUserToken` (audience = gatewayAudience, TTL ≤15m). Refresh/rotate/logout có endpoint.
- **Data writes**: user account, refresh entity, mfa challenge/authenticator/recovery.
- **Completeness**: **COMPLETE** ở mức bề mặt (chi tiết AuthService chưa đọc sâu — UNKNOWN mức cạnh tranh refresh rotation).

### B2. Service token (M2M)
- **Actor**: service (vd order) gọi identity để lấy token theo audience.
- **Flow** (FACT, `ServiceTokenService.issue`): kiểm `clientId` + bcrypt secret (`encoder.matches`), kiểm `mayTarget(audience)`, cấp token scope theo audience, TTL (`service-token.ttl`, mặc định 5m, cap 15m). Audit log mọi grant/deny.
- **Lưu ý**: `service-token.clients: {}` rỗng mặc định ⇒ phải whitelist mỗi client theo profile (an toàn mặc định). **COMPLETE**.

### B3. RBAC / authorization
- **Flow** (FACT): gRPC `IdentityDirectoryService.checkPermission/batchCheckPermissions` → `AuthorizationService`; policy per-method qua `IdentityGrpcSecurityConfiguration` (map method→authority: PRINCIPAL_READ/UPDATE, PERMISSION_CHECK, ROLE_*, USER_*, PLATFORM_*). Tenant isolation: `GrpcRequestSecurity.authorize*` dẫn `securedRequest.tenantId()` từ context đã xác thực (so khớp `x-tenant-id` ở interceptor).
- **Completeness**: **COMPLETE**.

### B4. MQTT command (membership/role qua event)
- **Actor**: hệ thống/admin phát command qua MQTT (`smartfarm/{t}/identity/command/{type}/v1`).
- **Flow** (FACT): `IdentityMqttCommandIntake.accept` validate chặt + dedup → inbox; `IdentityMqttCommandDispatcher.dispatch` (poll 1s) thực thi membership enable/suspend/disable + role assign/revoke; phát `identity.command.result`.
- **Completeness**: **PARTIAL** — thực thi COMPLETE nhưng kết quả **orphan** (ISSUE-06); dispatcher head-of-line blocking (ISSUE-09).

## C. Tổng hợp lớp hoàn thiện
| Workflow | Completeness |
|---|---|
| PlaceOrder | COMPLETE |
| CancelOrder | COMPLETE |
| Saga compensation | COMPLETE (ISSUE-03 drift tiềm ẩn) |
| Order state `TASK_SCHEDULED` | PARTIAL / UNKNOWN |
| Login + MFA | COMPLETE (chi tiết race UNKNOWN) |
| Service token | COMPLETE |
| RBAC check | COMPLETE |
| MQTT identity command | PARTIAL (result orphan) |
| MQTT message security | NOT_IMPLEMENTED (ISSUE-02) |
