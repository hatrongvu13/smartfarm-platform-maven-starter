# Tài liệu FarmVerse

| Tài liệu | Nội dung |
|---|---|
| [Đọc hiểu mã theo luồng](code-walkthrough.md) | **bắt đầu ở đây**: bức tranh lớn → kết nối → từng luồng kèm class → bảng class và cây thư mục |
| [Kiến trúc: hiện tại, mục tiêu, mô hình đe dọa](architecture/README.md) | bản đồ kiến trúc có bằng chứng, ADR |
| [Thiết kế FarmVerse](../content/thiet-ke-farmverse.md) | tài liệu thiết kế: phạm vi, kiến trúc, mTLS, gRPC, quyết định đã chốt |
| [BFF + Token Relay](patterns/bff.md) | gateway giữ token thay trình duyệt |
| [OIDC với Spring Authorization Server](patterns/oidc-sas.md) | quyền nằm trong JWT |
| [Hexagonal + multi-tenant](patterns/hexagonal-multitenant.md) | farm-service |
| [mTLS giữa service](patterns/mtls.md) | SSL bundles, SPIFFE SAN, hai lớp kiểm |
| [Chạy trên máy](operations/local-development.md) | thứ tự khởi động, biến môi trường |
| [Identity: OpenAPI và dạng lỗi](operations/identity-api.md) | Swagger UI ở dev, mã lỗi `SF-*` |
| [Xoay khóa ký JWT](operations/key-rotation.md) | quy trình xoay khóa, khóa nghỉ |
| [Scripts](../scripts/README.md) | script tiện ích |
