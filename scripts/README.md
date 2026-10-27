# Scripts

| Script | Việc | Ghi chú |
|---|---|---|
| `doctor.sh` | kiểm JDK, Maven và các module khai trong `pom.xml` | chỉ đọc |
| `bootstrap.sh` | kiểm công cụ + `mvn clean install` lần đầu | `--skip-build` để chỉ kiểm |
| `generate-dev-keys.sh` | cặp khóa RSA 3072 ký JWT cho identity | từ chối ghi đè; `--force` làm mọi token cũ mất hiệu lực |
| `generate-secret.sh [bytes]` | chuỗi ngẫu nhiên base64 (mặc định 32 byte) | chỉ in ra màn hình |
| `generate-dev-mtls.sh [dir] [--force]` | CA dev + cert cho identity, farm, gateway (SAN `spiffe://farmverse/<service>`) | hạn 30 ngày; `--force` tạo lại |

Không commit file `.pem` hay `.key`.
