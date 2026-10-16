# Danh Mục & Hướng Dẫn Vận Hành Scripts — SmartFarm Platform

> **Chuẩn hóa:** 2026-10-16  
> **Nguyên tắc:** Thống nhất toàn bộ script tạo và cấu hình về thư mục `scripts/` làm điểm quản trị duy nhất (Single Point of Truth). Mọi script đều được mô tả chi tiết: mục đích sử dụng, cú pháp, hệ quả tác động (file, database, process, security), và các cảnh báo rủi ro quan trọng.

---

## 1. Bảng Tổng Hợp Danh Mục Scripts

| Script | Nhóm | Mục Đích Chính | Mức Độ Rủi Ro |
|---|:---:|---|:---:|
| [`doctor.sh`](#1-doctorsh) | Chẩn đoán | Kiểm tra JDK 17+, Maven 3.9+, Python 3 và tính toàn vẹn 14 module POM | 🟢 An toàn (Read-only) |
| [`bootstrap.sh`](#2-bootstrapsh) | Khởi tạo | Thiết lập ban đầu môi trường monorepo, kiểm tra toolchain & build cài đặt `mvn clean install` | 🟡 Trung bình (Ghi `~/.m2`) |
| [`build-all.sh`](#3-build-allsh) | Biên dịch | Chạy `mvn -B clean verify` toàn bộ dự án từ root monorepo | 🟢 An toàn (Biên dịch) |
| [`generate-dev-keys.sh`](#4-generate-dev-keyssh) | Cấu hình & Bảo mật | Tạo cặp khóa RSA 3072-bit (private/public key) ký JWT cho Identity Service | 🔴 Cao (Nguy cơ hủy token cũ) |
| [`generate-dev-tls.sh`](#5-generate-dev-tlssh) | Cấu hình & Bảo mật | Tạo chứng chỉ x509 và TLS key cho localhost phục vụ gRPC/HTTPS Dev | 🟡 Trung bình (Cần truststore) |
| [`generate-secret.sh`](#6-generate-secretsh-randsh) | Cấu hình & Bảo mật | Sinh chuỗi ngẫu nhiên chuẩn mật mã (base64) cho HMAC, AES MFA, Cookies | 🟢 An toàn (Chỉ in ra stdout) |
| [`run-all.sh`](#7-run-allsh) | Kiểm thử HTTP | Điều phối chạy bộ kiểm tra Health và Smoke qua Gateway | 🟢 An toàn (Read-only probe) |
| [`health/test-health.sh`](#8-healthtest-healthsh) | Kiểm thử HTTP | Kiểm tra Actuator Health của Gateway và các service trực tiếp | 🟢 An toàn (Read-only probe) |
| [`smoke/smoke-test.sh`](#9-smokesmoke-testsh) | Kiểm thử HTTP | Kiểm tra GraphQL `platformStatus`, OpenAPI docs và `/api/v1/me` | 🟢 An toàn (Read-only probe) |
| [`config-forward.sh`](#10-config-forwardsh) | Mạng Dev | Forward các cổng TCP nội bộ (8022, 1883, 5432, 6379) sang thiết bị Android qua ADB | 🟡 Trung bình (Giữ kết nối ADB) |
| [`dev/rebuild-restart.sh`](#11-devrebuild-restartsh) | Vận hành Dev | Kill các tiến trình JVM cũ trên cổng 8080-8097, build và khởi động lại ngầm | 🔴 Rất Cao (Kill process & spawn daemons) |

---

## 2. Hướng Dẫn Chi Tiết Từng Script

---

### 1. `doctor.sh`

#### Mục đích
Kiểm tra sức khỏe môi trường phát triển monorepo trước khi bắt đầu làm việc. Script tự động xác minh:
- Phiên bản Java (yêu cầu JDK 17+).
- Phiên bản Maven (yêu cầu Maven 3.9+).
- Trích xuất toàn bộ thẻ `<module>` trong root `pom.xml` bằng Python 3 và kiểm tra sự tồn tại thực tế của từng file `pom.xml` con trên đĩa cứng.

#### Cách dùng
```bash
./scripts/doctor.sh
```

#### Hệ quả thực thi
- **Filesystem / Database:** Hoàn toàn không sửa đổi hoặc tạo mới file nào; không kết nối cơ sở dữ liệu.
- **Process:** Không khởi chạy tiến trình nền (background daemon). Kết thúc ngay sau khi in kết quả kiểm tra.
- **Mã thoát (Exit Code):** Trả về `0` nếu toàn bộ công cụ và module hợp lệ; trả về `1` nếu phát hiện thiếu công cụ hoặc module con bị mất `pom.xml`.

#### Lưu ý & Rủi ro
- Script yêu cầu máy trạm phải có `python3` trong `$PATH`.
- Là script an toàn tuyệt đối, nên chạy đầu tiên khi clone dự án hoặc sau khi chuyển branch lớn.

---

### 2. `bootstrap.sh`

#### Mục đích
Thiết lập ban đầu dự án từ con số 0 sau khi clone về máy mới. Script xác nhận toolchain và thực thi `mvn -B clean install` toàn bộ reactor để sinh mã nguồn Protobuf và cài đặt các artifact nội bộ (`libs/smartfarm-*`) vào kho cục bộ `~/.m2/repository`.

#### Cách dùng
```bash
# Thiết lập đầy đủ (kiểm tra tools + biên dịch cài đặt toàn bộ reactor)
./scripts/bootstrap.sh

# Chỉ kiểm tra toolchain, không thực hiện build
./scripts/bootstrap.sh --skip-build
```

#### Hệ quả thực thi
- **Filesystem:** Sinh ra các thư mục `target/` trong toàn bộ 14 module con (chứa bytecode `.class`, code sinh từ `.proto`, file JAR thành phẩm).
- **Maven Local Repo:** Cập nhật các JAR `com.htv.smartfarm:*` vào thư mục `~/.m2/repository/com/htv/smartfarm/`.
- **Database / Network:** Không can thiệp vào PostgreSQL hay MQTT. Cần kết nối Internet (hoặc Maven offline cache) để tải các dependency Spring Boot.

#### Lưu ý & Rủi ro
- Thời gian chạy có thể mất từ 1 đến 3 phút tùy tốc độ máy và mạng.
- Nếu build thất bại, kiểm tra lại biến môi trường `JAVA_HOME` trỏ đúng JDK 17+.

---

### 3. `build-all.sh`

#### Mục đích
Biên dịch, đóng gói và thực thi xác minh toàn diện toàn bộ 14 module trong monorepo từ thư mục gốc thông qua lệnh chuẩn: `mvn -B clean verify "$@"`. Cho phép chuyển tiếp bất kỳ cờ lệnh Maven bổ sung nào.

#### Cách dùng
```bash
# Biên dịch và chạy toàn bộ test
./scripts/build-all.sh

# Biên dịch nhanh, bỏ qua kiểm thử tự động
./scripts/build-all.sh -DskipTests

# Biên dịch ngoại tuyến (offline mode)
./scripts/build-all.sh -o -DskipTests
```

#### Hệ quả thực thi
- Xóa sạch và tái tạo toàn bộ thư mục `target/` của các module.
- Chạy unit tests và integration tests (nếu không truyền `-DskipTests`).

#### Lưu ý & Rủi ro
- Không truyền các cờ can thiệp sâu trừ khi cần thiết. Script tuân thủ nghiêm ngặt chuẩn Maven Batch Mode (`-B`).

---

### 4. `generate-dev-keys.sh`

#### Mục đích
Tạo cặp khóa mật mã bất đối xứng RSA 3072-bit (`identity-private.pem` và `identity-public.pem`) dùng cho thuật toán ký số RS256 của JWT do Identity Service phát hành. Được đồng nhất về thư mục `scripts/` và lưu trữ mặc định tại `services/smartfarm-identity-service/src/main/resources/.local/`.

#### Cách dùng
```bash
# Chạy tạo khóa an toàn (tự động từ chối ghi đè nếu đã có khóa)
./scripts/generate-dev-keys.sh

# Tùy chọn đường dẫn thư mục lưu trữ khác
./scripts/generate-dev-keys.sh /path/to/custom/.local

# BẮT BUỘC ghi đè cặp khóa hiện tại (CẨN TRỌNG!)
./scripts/generate-dev-keys.sh --force
```

#### Hệ quả thực thi
- **Filesystem:** 
  - Tạo mới `identity-private.pem` với quyền đọc giới hạn nghiêm ngặt `chmod 600` (`-rw-------`).
  - Tạo mới `identity-public.pem` với quyền `chmod 644` (`-rw-r--r--`).
  - Thư mục `.local` được phân quyền `umask 077`.
- **Hệ thống bảo mật:** Nếu thực hiện ghi đè (`--force`), **toàn bộ các Token (User JWT, S2S Delegated Token, Async Token) đã cấp trước đó sẽ lập tức trở nên KHÔNG HỢP LỆ (Cryptographically Invalid)** vì chữ ký số không còn khớp với public key mới.

#### Lưu ý & Rủi ro
> [!CAUTION]
> **RỦI RO MẤT HIỆU LỰC TOÀN BỘ PHIÊN:**
> Không bao giờ chạy với cờ `--force` trên môi trường đang có phiên đăng nhập của người dùng hoặc worker SAGA đang chạy dở, trừ khi bạn cố ý thiết lập lại toàn bộ hạ tầng bảo mật từ đầu.
> Tuyệt đối không commit các file private key `.pem` vào Git repository!

---

### 5. `generate-dev-tls.sh`

#### Mục đích
Tạo chứng chỉ số x509 tự ký (Self-signed Certificate) và private key RSA 3072-bit thời hạn 30 ngày cho `localhost` và IP `127.0.0.1` (`identity-tls.crt`, `identity-tls.key`) nhằm phục vụ giao thức bảo mật đường truyền (TLS/mTLS) cho cổng gRPC của Identity Service trong môi trường Dev.

#### Cách dùng
```bash
# Tạo chứng chỉ TLS (tự động từ chối nếu file đã tồn tại)
./scripts/generate-dev-tls.sh

# Bắt buộc tạo mới chứng chỉ (khi chứng chỉ cũ hết hạn 30 ngày)
./scripts/generate-dev-tls.sh --force
```

#### Hệ quả thực thi
- **Filesystem:** Tạo ra `identity-tls.key` (`chmod 600`) và `identity-tls.crt` (`chmod 644`) tại `services/smartfarm-identity-service/src/main/resources/.local/`.
- **Chứng thực:** Cần import chứng chỉ `identity-tls.crt` vào truststore của Gateway JVM (`gateway-truststore.p12`) nếu cấu hình bật xác thực chứng chỉ TLS.

#### Lưu ý & Rủi ro
- Chứng chỉ tự ký chỉ có giá trị trong 30 ngày kể từ ngày sinh. Nếu sau 30 ngày cuộc gọi gRPC TLS báo lỗi `CertificateExpiredException`, hãy chạy lại script với cờ `--force`.

---

### 6. `generate-secret.sh` (& `rand.sh`)

#### Mục đích
Sinh chuỗi ngẫu nhiên chuẩn mật mã (Cryptographically Secure Random String) được mã hóa Base64 với độ dài byte tùy chỉnh (mặc định 32 bytes = 256 bits). Dùng để tạo:
- Khóa bí mật mã hóa đối xứng AES-256 cho TOTP MFA (`identity-mfa-encryption-key`).
- Secret key ký HMAC envelope cho sự kiện MQTT (`SFM1`).
- Secret hash cho cookie / session session storage.

#### Cách dùng
```bash
# Sinh chuỗi bí mật ngẫu nhiên 32 bytes (256 bits)
./scripts/generate-secret.sh

# Sinh chuỗi bí mật độ dài 64 bytes (512 bits)
./scripts/generate-secret.sh 64

# Sử dụng qua wrapper tương thích rand.sh
./scripts/rand.sh
```

#### Hệ quả thực thi
- Chỉ in chuỗi ngẫu nhiên ra tiêu chuẩn `stdout`. Không tự ý ghi đè file cấu hình.

#### Lưu ý & Rủi ro
- Nếu gán key này vào biến môi trường hoặc file `.local/identity-mfa-encryption-key`, hãy đảm bảo file được phân quyền `chmod 600`.

---

### 7. `run-all.sh`

#### Mục đích
Bộ điều phối kiểm thử HTTP thống nhất (Unified Test Runner). Tự động kiểm tra các công cụ phụ thuộc (`bash`, `curl`, `jq`), nạp cấu hình môi trường từ `scripts/config/test.env`, và chạy tuần tự các giai đoạn:
1. Giai đoạn `health`: Gọi `scripts/health/test-health.sh`.
2. Giai đoạn `smoke`: Gọi `scripts/smoke/smoke-test.sh`.

#### Cách dùng
```bash
# Chạy toàn bộ các pha kiểm thử với cấu hình mặc định (test.env)
./scripts/run-all.sh

# Chạy với file cấu hình chỉ định
./scripts/run-all.sh path/to/custom.env

# Chỉ chạy riêng pha health
ONLY=health ./scripts/run-all.sh

# Chỉ chạy riêng pha smoke
ONLY=smoke ./scripts/run-all.sh
```

#### Hệ quả thực thi
- **Filesystem / Database:** Hoàn toàn là thao tác đọc (read-only); **tuyệt đối không làm biến đổi dữ liệu (no mutation)** trong database.
- **Process:** Yêu cầu Gateway và các service liên quan **phải đang hoạt động từ trước**. Script không tự động bật/tắt service.
- **Mã thoát (Exit Code):**
  - `0`: Tất cả các pha chạy thành công.
  - `1`: Một hoặc nhiều pha gặp lỗi xác nhận (assertion fail).
  - `2`: Sai cú pháp hoặc thiếu file cấu hình.
  - `3`: Thiếu công cụ phụ thuộc (`curl`, `jq`...).

#### Lưu ý & Rủi ro
- Script sẽ bỏ qua (SKIP) các service không cấu hình URL thay vì báo lỗi đỏ, giúp môi trường dev linh hoạt khi chỉ bật một phần hệ thống.

---

### 8. `health/test-health.sh`

#### Mục đích
Gửi yêu cầu HTTP GET thăm dò trạng thái Actuator (`/actuator/health`) của Gateway (bắt buộc) và các service nội bộ nếu có khai báo biến môi trường tương ứng:
- `IDENTITY_HEALTH_URL` (mặc định: `http://localhost:8092/actuator/health`)
- `ORDER_HEALTH_URL` (mặc định: `http://localhost:8085/actuator/health`)
- `INVENTORY_HEALTH_URL` (mặc định: `http://localhost:8083/actuator/health`)
- `FINANCE_HEALTH_URL` (mặc định: `http://localhost:8084/actuator/health`)
- `LIVESTOCK_HEALTH_URL`, `HEALTH_HEALTH_URL`, `REPORTING_HEALTH_URL`, `READINESS_HEALTH_URL`.

#### Cách dùng
```bash
./scripts/health/test-health.sh
./scripts/health/test-health.sh scripts/config/test.env
```

#### Hệ quả thực thi
- Chỉ đọc endpoint Actuator. Yêu cầu mã phản hồi HTTP `200` và JSON có chứa trường `status: "UP"`.

#### Lưu ý & Rủi ro
- Gateway là điều kiện tiên quyết (precondition): nếu Gateway chết hoặc không phản hồi, script sẽ fail ngay lập tức.

---

### 9. `smoke/smoke-test.sh`

#### Mục đích
Kiểm tra luồng thông suốt (Smoke Test) nhanh chóng thông qua Gateway:
1. Thăm dò Gateway Actuator Health (`/actuator/health`).
2. Gửi truy vấn GraphQL `platformStatus { name status }` đến `/graphql`.
3. Kiểm tra tính sẵn sàng của tài liệu OpenAPI Docs (`/v3/api-docs`).
4. Nếu có khai báo `TEST_USERNAME` và `TEST_PASSWORD`, script sẽ thực hiện đăng nhập và gọi GET `/api/v1/me` để xác thực JWT.

#### Cách dùng
```bash
./scripts/smoke/smoke-test.sh
```

#### Hệ quả thực thi
- Read-only probe. Không tạo đơn hàng, không thêm sửa xóa tài khoản hay thay đổi số dư.

#### Lưu ý & Rủi ro
- Nếu tài khoản bật MFA mà không cấu hình `TEST_TOTP_CODE`, bước xác thực `/api/v1/me` sẽ được đánh dấu SKIP an toàn.

---

### 10. `config-forward.sh`

#### Mục đích
Hỗ trợ kiểm thử trên thiết bị di động Android hoặc máy ảo qua cổng USB kết nối với máy Mac phát triển. Tự động tìm kiếm thiết bị qua công cụ `adb` (Android Debug Bridge) và thiết lập ánh xạ cổng ngược (Port Forwarding) từ điện thoại về máy trạm cho các cổng:
- `8022`: SSH Server nội bộ.
- `1883`: MQTT Mosquitto Broker.
- `5432`: PostgreSQL Database.
- `6379`: Redis Server.

#### Cách dùng
```bash
./scripts/config-forward.sh
# Nhấn Ctrl+C để dừng duy trì kết nối khi kết thúc buổi làm việc
```

#### Hệ quả thực thi
- Chạy lệnh `adb forward tcp:<port> tcp:<port>`. Duy trì tiến trình ở foreground cho tới khi nhấn `Ctrl+C`.
- Không tác động vào mã nguồn hay database.

#### Lưu ý & Rủi ro
- Yêu cầu máy tính đã cài đặt `adb` (Android platform-tools) và điện thoại đã bật chế độ "USB Debugging".
- Nếu các cổng trên máy tính bị ứng dụng khác chiếm giữ, lệnh forward sẽ thông báo lỗi cổng đó.

---

### 11. `dev/rebuild-restart.sh`

#### Mục đích
Script hỗ trợ nâng cao dành riêng cho Developer khi cần **hard-restart** đồng bộ toàn bộ hoặc từng phần JVM của các microservice. Script giải quyết triệt để lỗi "Stale gRPC stub / Unimplemented RPC" xảy ra khi service JVM cũ chưa giải phóng cổng gRPC trước khi mã nguồn Proto mới được build.

#### Cách dùng (Chỉ chạy thủ công từ terminal)
```bash
# Biên dịch lại proto + toàn bộ service và khởi động lại tất cả
bash scripts/dev/rebuild-restart.sh

# Chỉ khởi động lại các service chỉ định (ví dụ order, finance, gateway)
bash scripts/dev/rebuild-restart.sh order finance gateway

# Bỏ qua khâu biên dịch Maven, chỉ kill và khởi động lại nhanh
SKIP_BUILD=1 bash scripts/dev/rebuild-restart.sh
```

#### Hệ quả thực thi
> [!CAUTION]
> **TÁC ĐỘNG MẠNH LÊN HỆ THỐNG TIẾN TRÌNH (PROCESSES):**
> 1. **Kill Processes:** Quét toàn bộ tiến trình đang lắng nghe trên các cổng `8080` (Gateway), `8092` (Identity), `9091` (Livestock), `9093` (Inventory), `9094` (Finance), `9095` (Order), `9096` (Reporting), `9097` (Health) và thực hiện `kill -9` cưỡng chế.
> 2. **Chạy ngầm (nohup):** Kích hoạt lại từng service bằng lệnh nền `nohup mvn -pl <module> spring-boot:run > /tmp/smartfarm-dev-logs/<svc>.log 2>&1 &`.
> 3. **Chờ khởi động:** Polling log tối đa 90 giây cho đến khi nhận được tín hiệu `Started` từ Spring Boot.

#### Lưu ý & Rủi ro
- **TUYỆT ĐỐI KHÔNG CHẠY TỰ ĐỘNG:** Tuân thủ **Quy tắc 1** — Trợ lý AI và các tác vụ CI/CD tự động không được tự ý thực thi script này vì nó chiếm dụng cổng và sinh tiến trình daemon ngầm khó kiểm soát.
- Yêu cầu PostgreSQL và MQTT broker phải đang chạy trước khi thực thi script này.
- Log chi tiết từng service được ghi tại: `/tmp/smartfarm-dev-logs/<tên-service>.log`.

---

## 3. Cấu Trúc Thư Mục `scripts/lib/` (Thư Viện Dùng Chung)

Các file trong `scripts/lib/` đóng vai trò là module hàm tiện ích được các script kiểm thử nạp (source) vào:
- `lib/common.sh`: Quản lý nạp biến môi trường (`load_env`), kiểm tra dependencies (`require_cmd`), định dạng màu sắc log (`log_info`, `log_success`, `log_error`), và mã thoát chuẩn.
- `lib/http.sh`: Đóng gói các hàm gọi `curl` (`http_get`, `http_post`, `graphql_request`), tự động che giấu (mask) Bearer Token trong log để chống rò rỉ secret.
- `lib/auth.sh`: Quản lý quy trình đăng nhập lấy token JWT từ Gateway (`auth_login`), xử lý thử thách MFA TOTP.
- `lib/assertions.sh`: Cung cấp các hàm kiểm tra kết quả (`assert_status`, `assert_json_valid`, `assert_field_exists`, `assert_eq`).

---

## 4. Quy Chuẩn Vận Hành An Toàn (Safety Guidelines)

1. **Bảo Mật Khóa và Bí Mật:**
   - Tuyệt đối không xóa bỏ cơ chế bảo vệ chống ghi đè trong `generate-dev-keys.sh` và `generate-dev-tls.sh`.
   - File cấu hình thực tế `scripts/config/test.env` đã được đưa vào `.gitignore`. Không bao giờ commit thông tin tài khoản thật vào kho mã nguồn.
2. **Kiểm Soát Tiến Trình:**
   - Luôn kiểm tra các tiến trình đang chạy bằng `lsof -i :<port>` hoặc `ps aux | grep java` trước khi khởi chạy service thủ công để tránh xung đột cổng (BindException).
3. **Môi Trường Cơ Sở Dữ Liệu:**
   - Trong môi trường phát triển local (Dev), các service dùng chung cơ sở dữ liệu `jdbc:postgresql://localhost:5432/smartfarm`. Mặc dù dùng chung instance, các service được cô lập bằng tiền tố bảng riêng biệt. Khi chạy script, không thực hiện các thao tác `DROP DATABASE` hoặc `TRUNCATE` ngoài phạm vi được chỉ định.
