# Tài liệu FarmVerse

| Tài liệu | Nội dung |
|---|---|
| [Kiến trúc: hiện tại, mục tiêu, mô hình đe dọa, roadmap](architecture/README.md) | bản đồ kiến trúc có bằng chứng, rủi ro P0-P3, ADR |
| [Thiết kế FarmVerse](../content/thiet-ke-farmverse.md) | tài liệu thiết kế duy nhất: phạm vi, tiêu chí 10/10, mTLS, gRPC, lộ trình |
| [BFF + Token Relay](patterns/bff.md) | gateway giữ token thay trình duyệt |
| [OIDC với Spring Authorization Server](patterns/oidc-sas.md) | quyền nằm trong JWT |
| [Hexagonal + multi-tenant](patterns/hexagonal-multitenant.md) | farm-service |
| [mTLS giữa service](patterns/mtls.md) | SSL bundles, SPIFFE SAN, hai lớp kiểm |
| [Chạy trên máy](operations/local-development.md) | thứ tự khởi động, biến môi trường |
| [Identity: OpenAPI và dạng lỗi](operations/identity-api.md) | Swagger UI ở dev, mã lỗi `SF-*` |
| [Xoay khóa ký JWT](operations/key-rotation.md) | quy trình xoay khóa, khóa nghỉ |
| [Scripts](../scripts/README.md) | script tiện ích |
