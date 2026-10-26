# Sơ đồ — Luồng lỗi lúc khởi động (boot)

Cây quyết định: vì sao một service dùng lib security chết lúc khởi động.

```mermaid
flowchart TD
    START[Service khởi động] --> DEP{Có dùng lib<br/>smartfarm-security?}
    DEP -->|Không| OK1[Chạy bình thường]
    DEP -->|Có| JWT{Có khối<br/>smartfarm.security.jwt?}

    JWT -->|Không| F1["CHẾT: jwt is required<br/>(Tập 01 — farm-simulator)"]
    JWT -->|Có| VAL{issuer & jwk-set-uri<br/>có giá trị thật?}

    VAL -->|Rỗng / blank| F2["CHẾT: validation fail<br/>(Tập 02 — reporting/finance prod)"]
    VAL -->|Có| AUD{audiences<br/>không rỗng?}

    AUD -->|Rỗng| F3["CHẾT: audiences NotEmpty"]
    AUD -->|Có| GRPC{require-explicit-policy<br/>= true?}

    GRPC -->|Không| OK2[Chạy — nhưng method<br/>chưa khai = chỉ cần token<br/>Tập 06]
    GRPC -->|Có| COV{Mọi method proto<br/>đều đã khai scope?}

    COV -->|Không| F4["CHẾT: coverage check<br/>unclassified methods<br/>(rủi ro 6 service)"]
    COV -->|Có| OK3[Chạy, gác chặt đúng chuẩn]

    classDef fail fill:#7a1f1f,stroke:#ff6b6b,color:#fff;
    classDef ok fill:#1f5a2f,stroke:#6bff9b,color:#fff;
    class F1,F2,F3,F4 fail;
    class OK1,OK2,OK3 ok;
```

## Đọc sơ đồ
- 3 nhánh đỏ bên trái (F1, F2, F3) là lỗi chặn boot do thiếu/rỗng cấu hình JWT.
- F4 là lỗi chặn boot do gRPC: bật kiểm chặt mà quên khai một method proto.
- OK2 (vàng-xanh) là trạng thái "chạy nhưng lỏng" của identity — không chết,
  nhưng không gác theo quyền.
