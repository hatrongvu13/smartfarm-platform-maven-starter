# Chiến lược repository

## Root repository

Root lưu manifest, scripts, compose, tài liệu, conventions và CI integration. Không copy source qua lại. `bootstrap.sh`
clone repository vào đúng đường dẫn. Có thể thay Git submodule nếu cần pin commit tuyệt đối, nhưng starter dùng
manifest + clone để thao tác đơn giản hơn.

## Versioning

- Thư viện common/proto publish vào Maven registry nội bộ bằng semantic version.
- Proto ưu tiên backward compatible: không tái sử dụng field number, chỉ additive change trong cùng `v1`; breaking
  change tạo `v2`.
- Service pin version release; Maven Reactor build local source khi chạy từ root.
- CI từng repo build độc lập. CI root chạy contract, integration và end-to-end theo manifest commit/tag khóa.

## Tách nhiệm vụ common

- `common-kernel`: event envelope, error model, identifiers, outbox abstractions. Không chứa business entity.
- `security`: resource-server config, JWT validators, gRPC interceptors, method authorization.
- `proto`: protobuf/gRPC contract, không trộn entity JPA.
- `readiness`: aggregation sức khỏe, không trở thành service discovery.
- `farm-simulator`: phát telemetry/status giả lập, không phụ thuộc domain internals.

## Maven root

Root `pom.xml` chỉ có vai trò aggregator. Repo con dùng Spring Boot parent độc lập, vì thế chạy độc lập sau khi shared
libraries đã được cài vào local Maven repository hoặc publish registry.
