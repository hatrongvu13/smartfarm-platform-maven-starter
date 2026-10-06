# Resolved Issues — SmartFarm Platform

> Các issue đã được source code giải quyết, chuyển khỏi active checklist. Giữ lịch sử.

| ID | Severity | Mô tả | Resolved bởi | Bằng chứng |
|----|----------|-------|-------------|-------------|
| ISSUE-01 | CRITICAL | order-service không boot prod do sai cấu trúc key security (flat `smartfarm.security.{issuer,...}` vs nested `jwt.*`) | Config fix: nested `jwt.*` trong base + prod `application.yml` 7 service | Source: `services/*/src/main/resources/application.yml` + `application-prod.yml` |
| ISSUE-03 | CRITICAL | Reverse-finance idempotency key không nhất quán (inline vs worker saga path) | Dead inline saga path removed | `docs/remediation/ISSUE-03-execution-result.md` |
| ISSUE-04 | HIGH | Outbox relay head-of-line blocking khi gặp 1 event lỗi topic | Classified relay: dead+continue poison, break only infra | `docs/remediation/ISSUE-04-05-execution-result.md` |
| ISSUE-05 | HIGH | OrderChangedConsumer `cleanSession=false` + `MemoryPersistence` — mất QoS1 | FILE persistence + manual ACK after commit | `docs/remediation/ISSUE-04-05-execution-result.md` |
| ISSUE-08 | MEDIUM | Hai triển khai saga song song (inline vs persistent worker) | Inline path removed | `docs/audit/08-saga-dead-path-and-framework-assessment.md` |
| ISSUE-09 | MEDIUM | IdentityMqttCommandDispatcher head-of-line block (break) | Shared classifier; unknown error now continue | Register |
| ISSUE-14 | INFO | Pagination listOrders kẹp pageSize (không phải lỗi) | Verified bằng đọc source — kẹp đúng | Source: `OrderQueryService.list` |
| ISSUE-19 / CFG-01 | HIGH | 6 `application-test.yml` dùng flat security key → không boot test profile | Tất cả 7 `application-test.yml` đã chuyển nested `jwt:` form | Source: `services/*/src/main/resources/application-test.yml` |

> **Profile config**: 9/9 module runnable đã đồng bộ `active: ${SPRING_PROFILES_ACTIVE:dev}` + `spring-boot-maven-plugin` pin `dev` (commit `eaaa112`). Lỗi "Unable to determine Dialect" biến mất.
