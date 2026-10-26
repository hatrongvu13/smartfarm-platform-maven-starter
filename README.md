# SmartFarm Platform

Nông trại thông minh theo phong cách tu tiên huyền huyễn.
Thiết kế microservices (MSA), nhiều client: web và mobile.

Đây là khung trống để bắt đầu từ đầu.

## Có gì bây giờ

Chỉ có 1 file: `pom.xml` gốc.
Nó là parent (mọi module kế thừa) và là aggregator (liệt kê mọi module).
Danh sách module đang rỗng.

## Nền tảng

| Thứ | Bản |
|-----|-----|
| Java | 17 |
| Spring Boot | 4.1.1 |
| Build | Maven (monorepo) |
| groupId | `com.htv.smartfarm` |

## Cấu trúc sẽ dựng

```
smartfarm-platform-maven-starter/
├── pom.xml          # parent + aggregator
├── libs/            # thư viện dùng chung
├── services/        # microservices
└── apps/            # gateway, ứng dụng
```

## Thêm một module

1. Tạo thư mục, ví dụ `services/smartfarm-identity-service`.
2. Viết `pom.xml` cho module, `<parent>` trỏ về pom gốc.
3. Thêm 1 dòng vào `<modules>` của pom gốc:

```xml
<module>services/smartfarm-identity-service</module>
```

## Build

```bash
mvn clean install
```
