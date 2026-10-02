# ISSUE-03 — Execution Result (Saga Cleanup P0)

Ngày: 2026-10-02 · Branch: `main` · Maven: `/Users/jaxmac/sdk/apache-maven-3.9.16` (3.9.16 / JDK17 Temurin)
Chế độ: thực thi checklist 0→8. Classification: REMOVE + DOCUMENT + VERIFY (không RENAME/RESTRUCTURE; DEFER phần framework).

## 1. Files added / modified / deleted

| Action | File |
|---|---|
| DELETED | `services/smartfarm-order-service/src/main/java/com/htv/smartfarm/order/saga/OrderSagaOrchestrator.java` (238 dòng) |
| MODIFIED | `services/smartfarm-order-service/src/main/java/com/htv/smartfarm/order/grpc/FarmOrderGrpcService.java` (gỡ import+field+ctor param+assignment) |
| MODIFIED | `docs/audit/04-issue-register.md` (ISSUE-03 → RESOLVED, ISSUE-08 → RESOLVED) |
| ADDED | `docs/architecture/order-saga.md` (state machine + idempotency matrix + mermaid) |
| ADDED | `docs/audit/08-saga-dead-path-and-framework-assessment.md` (báo cáo đánh giá — turn trước) |
| ADDED | `docs/remediation/ISSUE-03-execution-result.md` (file này) |

Không file ngoài phạm vi đã khai báo bị sửa.

## 2. Dead references removed

- `OrderSagaOrchestrator` class — xoá hẳn (không `@Deprecated`: 0 caller, không public contract, tránh bean thừa + tái dùng drift).
- Import / field `saga` / ctor param / assignment trong `FarmOrderGrpcService` — gỡ.
- Idempotency key `:expense` / `:expense-reversal` (chỉ tồn tại trong class đã xoá) — biến mất khỏi source.
- Grep xác nhận: `OrderSagaOrchestrator` và `:expense` = 0 match trong source `services/` (chỉ còn trong docs/graphify artifacts).

## 3. Persistent saga invariants preserved

- `placeOrder()` → `persistentSagas.create(...)` (`FarmOrderGrpcService.java:141`) — giữ.
- `cancelOrder()` → `persistentSagas.requestCancellation(...)` (`:290`) — giữ.
- Step graph `reserve→finance→commit` + nhánh bù `reverse-finance→release` — không đổi.
- Forward `POST_FINANCE` = `orderId:finance`; `REVERSE_FINANCE.originalExpenseKey = orderId:finance` (`OrderSagaCompensationStepExecutor.java:59`) — KHỚP, không đổi.
- Retry/deadline/claim/manual-review (`OrderSagaProperties`) — không đổi.
- Transaction boundary: executor gọi gRPC ngoài `@Transactional` — không giữ DB tx qua remote call. Giữ nguyên.
- Outbox/event flow — không đụng.

## 4. Build / test commands & results

```bash
export MAVEN_HOME=/Users/jaxmac/sdk/apache-maven-3.9.16
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
"$MAVEN_HOME/bin/mvn" -pl services/smartfarm-order-service -am -DskipTests compile   # EXIT=0
"$MAVEN_HOME/bin/mvn" -pl services/smartfarm-order-service -am test                   # BUILD SUCCESS
```
- compile: EXIT=0 — không missing bean, không constructor mismatch, không dead reference.
- test: **BUILD SUCCESS** — order **22**, security **27** (gồm 8 MQTT), common **13**; 0 failure / 0 error.

## 5. Contract compatibility

- gRPC/proto: KHÔNG đổi.
- Database schema / Flyway: KHÔNG đổi (không migration).
- MQTT topic: KHÔNG đổi.
- Public endpoint: KHÔNG đổi.
- Spring bean name / serialized class / dashboard metric: không rename gì → không ảnh hưởng.

## 6. Remaining risks

- Không có production/test/dynamic caller còn lại (whole-repo grep + `new FarmOrderGrpcService(` = 0).
- DEFER (không fix lần này, theo checklist "không ép"): `OrderSagaTransactionService` ~340 LOC trộn transition+persistence+retry — ứng viên tách method nội bộ, chưa cần.
- Smoke runtime (khởi động Spring context, worker bean, place/cancel qua persistent saga) chưa chạy vì cần DB/broker — không tự dựng khi chưa được xác nhận. Compile+unit test đã chứng minh wiring hợp lệ.

## 7. Deferred generic-runtime backlog

- KHÔNG tạo `smartfarm-saga` ở phase này (chỉ 1 saga owner).
- Backlog gated **`SAGA-EN2 — Generic Saga Runtime Extraction Assessment`**: chỉ mở khi có saga owner thứ 2 đủ đặc điểm (multi-step, cross-service, long-lived, retry bền, compensation/manual-recovery, claim/checkpoint tương đồng, genericity đủ cao, giảm duplication thực). Chi tiết Candidate/Anti-Candidate ở `docs/audit/08`.

## 8. Rollback procedure

```bash
git revert <commit-cleanup>   # khôi phục OrderSagaOrchestrator.java + ctor cũ
```
Chỉ 1 file xoá + 1 file source sửa; docs độc lập. Không có migration/DB/contract để hoàn tác.

---

### Checklist status
0 Prep ✅ · 1 Final verify (SAFE_TO_REMOVE) ✅ · 2 Remove ✅ · 3 Persistent invariants ✅ ·
4 Structure (VERIFY, no change) ✅ · 5 Properties/naming (VERIFY, no change) ✅ ·
6 Docs ✅ · 7 Build/verify ✅ · 8 Register + result ✅
ISSUE-03 RESOLVED · ISSUE-08 RESOLVED · framework extraction DEFERRED (gated).
