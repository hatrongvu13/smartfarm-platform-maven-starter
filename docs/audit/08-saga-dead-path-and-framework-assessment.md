# ISSUE-03 — Dead Saga Validation + Shared Saga Framework Assessment

> Chế độ: **REPORT-ONLY**. Không sửa source, không tạo patch, không refactor trong tài liệu này
> (theo ràng buộc của prompt). Mọi kết luận gắn nhãn FACT / INFERENCE / UNKNOWN / RISK /
> RECOMMENDATION. Bằng chứng = đường dẫn file + symbol + số dòng + node/edge graph.
>
> Nguồn bằng chứng: đọc source trực tiếp + graphify graph (4.509 node / 13.727 edge).
> **Không** chạy `mvn dependency:tree` / scan env / docker / k8s (chưa cần, và theo ràng buộc
> `MAVEN_HOME` chỉ dùng khi được xác nhận — ở đây không cần Maven).

---

## Executive Summary

### Scope
- Xác minh `OrderSagaOrchestrator` có còn caller thực tế không (Phase 1).
- Kiểm kê kiến trúc saga đang chạy của order-service (Phase 2).
- Đánh giá khả năng tách saga thành module dùng chung `smartfarm-saga` (Phase 3–6).
- Đối chiếu order / identity / inventory / finance để chấm similarity (Phase 4).

### Key Findings
- **FACT — `OrderSagaOrchestrator` là dead-path hoàn toàn trong luồng nghiệp vụ.** Nó được
  *inject* vào `FarmOrderGrpcService` nhưng **không method nào của nó (`run` / `cancel` /
  `compensate`) được gọi ở bất kỳ đâu** — không trong gRPC surface, không test, không
  `@Scheduled`, không reflection, không admin controller.
- **FACT — idempotency drift chỉ nằm TRONG dead code.** Đường chết dùng `:expense`; đường
  worker đang chạy dùng `:finance` *nhất quán cả forward lẫn compensation*. Vì đường chết
  không bao giờ chạy, **hiện tại không có lệch sổ tiền thực tế** — rủi ro chỉ bật nếu ai đó
  tái dùng `OrderSagaOrchestrator`.
- **FACT — chỉ order-service có saga.** identity / inventory / finance là *participant*
  (command handler single-step, idempotent), không phải saga owner. Không tồn tại framework
  saga cạnh tranh.
- **INFERENCE — việc tách `smartfarm-saga` dùng chung CHƯA đáng làm bây giờ.** Chỉ có **một**
  saga owner. Trừu tượng hoá generic engine từ một cài đặt duy nhất là trừu tượng hoá non
  (speculative generality) — vi phạm chính nguyên tắc "chỉ trích xuất phần thực sự generic".

### Severity Summary
```text
CRITICAL: 0
HIGH:     1   (TD-01: dual saga path — gỡ dead code)
MEDIUM:   1   (TD-02: idempotency key drift tiềm ẩn trong dead code)
LOW:      1   (TD-03: tên/metrics "saga" trùng giữa 2 cài đặt gây nhiễu đọc hiểu)
INFO:     2   (AS-01 không tách module; AS-02 chuẩn hoá contract nội bộ)
```

---

## 1. Dead Saga Validation

### FACT — Caller thực tế
| Tham chiếu | File | Dòng | Loại |
|---|---|---|---|
| `import ...OrderSagaOrchestrator` | `services/smartfarm-order-service/src/main/java/com/htv/smartfarm/order/grpc/FarmOrderGrpcService.java` | 21 | import |
| `private final OrderSagaOrchestrator saga;` | `FarmOrderGrpcService.java` | 49 | field (bean inject) |
| ctor param `OrderSagaOrchestrator saga` | `FarmOrderGrpcService.java` | 58 | DI |
| định nghĩa class | `.../order/saga/OrderSagaOrchestrator.java` | 42 | định nghĩa |

**Không có một lời gọi method nào.** `grep` toàn `services/` cho `saga.run(`, `saga.cancel(`,
`saga.compensate(`, `.run(order` → 0 kết quả gọi (chỉ ra `SpringApplication.run` không liên quan).
`placeOrder()` dùng `persistentSagas.create(...)` (`FarmOrderGrpcService.java:131`);
`cancelOrder()` dùng `persistentSagas.requestCancellation(...)` (`FarmOrderGrpcService.java:299`).

### FACT — Dependency / bean / startup / test / reflection / event
- **Bean injection**: có (field `saga`), nhưng chỉ giữ reference, không dùng → đây chính là
  "injected-but-unused".
- **Startup wiring**: `@Service` nên Spring vẫn tạo bean lúc startup (xem RISK bên dưới).
- **Test references**: 0. Các test saga (`OrderSagaEntityHardeningTest`,
  `OrderSagaStepEntityHardeningTest`) chỉ chạm persistent path, không chạm orchestrator.
- **Reflection / bean-name lookup**: 0. `grep` `getBean(|forName(|ApplicationContext|BeanFactory`
  trong order-service → không có nơi nào resolve `OrderSagaOrchestrator` động.
- **Event-driven**: 0. Nó không implement listener, không `@EventListener`, không subscribe MQTT.
- **Graph edge**: chỉ còn `FarmOrderGrpcService --references--> OrderSagaOrchestrator`
  (import/field), **không có edge `CALLS`** tới `run/compensate` (khớp với audit 01/04).

### UNKNOWN
- **UNKNOWN-1 (dynamic invocation):** không phát hiện SpEL / `@ConditionalOnExpression` /
  profile nào bật một đường gọi orchestrator. Mức tin cậy "không có" = HIGH, nhưng vẫn để UNKNOWN
  vì không chạy app runtime để quan sát 100%.
- **UNKNOWN-2 (external module):** chỉ scan `services/`. Nếu có module/script ngoài cây
  `services/` gọi class này qua classpath (khó, vì nó `package-private`-free nhưng là
  order-internal) thì chưa loại trừ. Tin cậy "không có" = HIGH.

### RISK
- **RISK khi XÓA hoàn toàn (thấp):** vì không caller, xóa class + gỡ field/param khỏi
  `FarmOrderGrpcService` là an toàn về biên dịch. Rủi ro còn lại duy nhất: constructor của
  `FarmOrderGrpcService` đổi chữ ký → mọi test khởi tạo nó bằng `new FarmOrderGrpcService(...)`
  phải bỏ 1 tham số. **Phải grep test trước khi gỡ** (đã thấy test saga không `new` service này,
  nhưng cần xác nhận các gRPC test khác).
- **RISK khi chỉ DEPRECATE (thấp hơn về biên dịch, cao hơn về nợ kỹ thuật):** giữ class +
  `@Deprecated` vẫn để bean được tạo lúc startup và vẫn mở cửa cho người sau tái dùng → giữ
  nguyên drift `:expense`. Không khuyến nghị.
- **RISK tương thích ngược (không có):** class này không nằm trong contract công khai (gRPC/proto),
  không ai ngoài order-service biết tới. Gỡ không phá API/contract.

### RECOMMENDATION (Phase 1)
1. **Gỡ hẳn** `OrderSagaOrchestrator.java`.
2. Gỡ field `saga` + param ctor khỏi `FarmOrderGrpcService` (import dòng 21, field 49, param 58).
3. Grep + sửa mọi `new FarmOrderGrpcService(...)` trong test cho khớp ctor mới.
4. Build + test order-service để chứng minh (dùng `MAVEN_HOME` đã xác nhận
   `/Users/jaxmac/sdk/apache-maven-3.9.16`).
> Đây là **đề xuất**; chưa thực thi vì prompt yêu cầu report-only. Khi bạn duyệt, tôi làm patch
> + verify (ước lượng: S, ~30 phút).

---

## 2. Current Saga Architecture (đường đang chạy)

**FACT — một saga owner duy nhất: order-service, cơ chế persistent-worker.**

| Thuộc tính | Giá trị (FACT) | Bằng chứng |
|---|---|---|
| Saga owner | order-service | `order/saga/**` |
| Transaction state | bảng `order_saga` + `order_saga_step` (JPA, row-lock `findByIdForUpdate`) | `OrderSagaEntity`, `OrderSagaStepEntity`, `OrderSagaTransactionService` |
| Step graph | reserve×N → finance → commit×N (+ reverse-finance, release×N là nhánh bù) | `OrderSagaTransactionService.graph()` dòng ~ "graph(" |
| Compensation | step bù dựng sẵn trong graph, kích hoạt có điều kiện (financePosted / reservedLines / committedLines) | `beginCompensationLocked(...)` |
| Retry | exponential backoff `initial*2^(attempt-1)` cap `maximumRetry` | `OrderSagaTransactionService.retryDelay()` |
| Deadline | `processingDeadline` (30m), `compensationDeadline` (6h), `claimTimeout` (2m), `financeManualReviewWindow` (60m) | `OrderSagaProperties` |
| Idempotency | mỗi step key `orderId + ":" + stepKey`; finance = `orderId:finance` (forward & compensation KHỚP) | `graph()` + `OrderSagaCompensationStepExecutor.reverseFinance()` dòng 59 |
| Outbox integration | riêng biệt (`order/outbox/**`, `OrderOutboxRelay @Scheduled`) — không trộn vào saga | `OrderOutboxRelay.java:18,33` |
| Event integration | saga tiến → `OrderEventStore.appendBusiness` → outbox → MQTT | `FarmOrderGrpcService.placeOrder` |
| Worker | poll 1s, claim batch, forward/compensation step machine, manual-review | `OrderSagaPersistentWorker @Scheduled(1s)`, bật bằng `@ConditionalOnProperty smartfarm.order.saga.enabled=true` |
| Admin / observability | có — `OrderSagaAdministrationService/Controller`, `OrderSagaHealthIndicator`, `OrderOperationalMetrics` | `order/saga/administration/**`, `observability/**` |

**FACT — participant (không phải saga owner):**
- inventory-service: `InventoryCommands` (reserve / commit / release reservation) — single-step idempotent.
- finance-service: `FinanceCommands` (record / reverse expense) — single-step idempotent.
- identity-service: không có saga; outbox + command inbox, không multi-step orchestration.

---

## 3. Similarity Matrix Between Services

| Service | Create | Validate | Reserve | Commit | Compensate | Notify | Có saga owner? | Similarity → framework chung |
|---|---|---|---|---|---|---|---|---|
| order | ✅ | ✅ | ✅ (gọi inventory) | ✅ | ✅ (reverse finance + release) | ✅ (event) | **CÓ** | — (chính nó) |
| inventory | — | ✅ | — (nó LÀ bên bị reserve) | ✅ | ✅ (release) nhưng là command đơn | — | KHÔNG | **LOW** |
| finance | — | ✅ | — | ✅ (record) | ✅ (reverse) command đơn | — | KHÔNG | **LOW** |
| identity | ✅ (user/tenant) | ✅ | — | — | — | ✅ (event) | KHÔNG | **LOW** |

**INFERENCE:** không service nào khác có workflow multi-step cần orchestration + compensation
persistent. Điểm "giống" (reserve/commit/compensate) ở inventory/finance là *vai trò participant*,
không phải *nhu cầu sở hữu saga*. ⇒ Similarity tổng thể cho framework dùng chung = **LOW**.

---

## 4. Shared Saga Candidates / Anti-Candidates

### Candidates (generic — NẾU sau này có saga owner thứ 2)
```text
SagaProperties (batchSize, pollInterval, retryBaseDelay, retryMaxDelay, maxAttempts, claimTimeout)
SagaRetryPolicy      (exponential backoff thuần số — hiện ở retryDelay())
SagaStepStatus / SagaStatus (vòng đời trạng thái — hiện order-specific nhưng generic được)
SagaClaim            (record vận chuyển — generic được)
SagaWorker loop skeleton (claim→execute→checkpoint→retry) — khung generic
```

### Anti-Candidates (KHÔNG đưa vào common — business-specific)
```text
OrderSagaStepType (RESERVE_STOCK/POST_FINANCE/COMMIT_STOCK/REVERSE_FINANCE/RELEASE_STOCK)  ← inventory/finance domain
OrderSagaForwardStepExecutor / CompensationStepExecutor  ← gọi inventory/finance stub, chứa domain
graph() reserve→finance→commit  ← business workflow của order
beginCompensationLocked (committedLines → MANUAL_REVIEW)  ← chính sách nghiệp vụ order
OrderEntity / OrderLineEntity / OrderStatus mapping
```

**RISK:** nếu ép tách bây giờ, phần "generic" quá mỏng so với phần business → engine chung sẽ
đầy generic type / callback để nhét domain vào, làm order-service *khó đọc hơn* trước khi có
service thứ 2 hưởng lợi. Đây là anti-pattern "framework trước nhu cầu".

---

## 5. Proposed `smartfarm-saga` Module — ĐÁNH GIÁ

**RECOMMENDATION: KHÔNG tách `smartfarm-saga` ở thời điểm này (INFO/AS-01).**
Lý do (INFERENCE từ bằng chứng Phase 3–4): một saga owner duy nhất; các service khác là
participant single-step; "rule of three" chưa đạt. Thay vào đó:

- **Nếu/khi** xuất hiện saga owner thứ 2 (ví dụ một workflow multi-step ở reporting/identity),
  mới trích `smartfarm-saga` theo khung dưới đây. Giữ sẵn các *Candidate* ở trên làm ranh giới.
- Trước đó, việc nên làm là **chuẩn hoá tên/contract nội bộ** trong order-service (AS-02), không
  phải tách module.

Khung tham chiếu (chỉ khi đủ điều kiện):
```text
smartfarm-saga
├── api            (SagaDefinition, SagaStepDefinition, SagaStatus, SagaStepStatus, SagaClaim)
├── execution      (StepExecutor<CTX> interface — KHÔNG chứa stub domain)
├── persistence    (SagaEntity/StepEntity base + JPA repo generic)
├── worker         (poll/claim/checkpoint loop)
├── retry          (SagaRetryPolicy)
├── compensation   (ordering/activation policy generic)
├── metrics / observability
└── configuration  (SagaProperties)
```
```java
@ConfigurationProperties(prefix = "smartfarm.saga")
public record SagaProperties(
    int batchSize, Duration pollInterval,
    Duration retryBaseDelay, Duration retryMaxDelay,
    int maxAttempts, Duration claimTimeout) {}
```

---

## 6. Migration Roadmap

| Phase | Nội dung | Trạng thái điều kiện | Complexity | Backward-compat | Rollback |
|---|---|---|---|---|---|
| **P0 — Remove dead path** | Gỡ `OrderSagaOrchestrator` + injection; sửa test ctor | **ĐỦ ĐIỀU KIỆN NGAY** | LOW (S) | Không phá contract | revert commit |
| P1 — Standardize saga contracts | Thống nhất naming/enum trong order-service, tài liệu hoá state machine | Nên làm sau P0 | LOW–MED | nội bộ | revert |
| P2 — Extract generic execution engine | CHỈ khi có owner #2 | **CHƯA đủ điều kiện** | HIGH | — | — |
| P3 — Extract persistence worker | CHỈ khi có owner #2 | CHƯA đủ | HIGH | — | — |
| P4 — Consolidate retry/compensation | CHỈ khi có owner #2 | CHƯA đủ | MED | — | — |
| P5 — Remove duplicated impl | Không áp dụng (không có bản sao thực) | N/A | — | — | — |

---

## 7. Backlog Items

```text
EPIC    SAGA-E1  "Dọn nợ kỹ thuật dual-saga + chuẩn hoá saga order-service"
  FEATURE  SAGA-F1  "Gỡ dead-path OrderSagaOrchestrator"            (P0, HIGH, S)
    STORY    SAGA-S1  Xoá OrderSagaOrchestrator.java
    STORY    SAGA-S2  Gỡ field/param khỏi FarmOrderGrpcService
    STORY    SAGA-S3  Sửa test ctor + build/test xanh (Maven xác nhận)
  FEATURE  SAGA-F2  "Chuẩn hoá contract/naming saga nội bộ order"  (P1, LOW, M)
    ENABLER  SAGA-EN1 Tài liệu state machine order_saga + bảng key idempotency
  FEATURE  SAGA-F3  "[GATED] Tách smartfarm-saga"                   (P2+, chờ owner #2)
    ENABLER  SAGA-EN2 Định nghĩa ranh giới Candidate/Anti-Candidate (tài liệu này là bản nháp)
```

---

## 8. Final Recommendation

### Must Do
- **P0 — Gỡ dead-path `OrderSagaOrchestrator`** (HIGH). Khép ISSUE-03/08. An toàn, revert-được,
  không phá contract. Loại bỏ nguồn drift `:expense` ngay tại gốc.

### Should Do
- **P1 — Chuẩn hoá + tài liệu hoá** state machine và bảng idempotency key của order-service
  (giảm nhiễu đọc hiểu do 2 cái tên "saga" cùng tồn tại).

### Nice To Have
- **Gated** — chỉ cân nhắc `smartfarm-saga` khi có saga owner thứ 2. Giữ tài liệu này làm ranh
  giới trích xuất; không tách sớm.

---

### Nhãn tổng kết
- **FACT:** orchestrator là dead-path; drift chỉ trong dead code; worker dùng `:finance` nhất quán; một saga owner duy nhất.
- **INFERENCE:** chưa đủ điều kiện tách module chung; nên gỡ dead code + chuẩn hoá nội bộ trước.
- **UNKNOWN:** dynamic/external invocation (tin cậy "không" = HIGH, chưa quan sát runtime).
- **RISK:** gỡ xong phải sửa ctor test; deprecate thay vì xóa sẽ giữ nợ + cửa tái dùng drift.
- **RECOMMENDATION:** P0 gỡ dead-path ngay (chờ bạn duyệt để tôi patch + verify).
