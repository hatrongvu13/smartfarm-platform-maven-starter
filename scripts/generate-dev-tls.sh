#!/usr/bin/env bash
# ==============================================================================
# scripts/generate-dev-tls.sh
# Mục đích: Tạo chứng chỉ số và private key TLS (x509 rsa:3072, sha256) cho
#          localhost / 127.0.0.1 phục vụ mã hóa đường truyền gRPC / HTTPS nội bộ
#          giữa Gateway và Identity Service trong môi trường Dev.
# ==============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${1:-$ROOT_DIR/services/smartfarm-identity-service/src/main/resources/.local}"
FORCE=0

for arg in "$@"; do
  if [ "$arg" = "--force" ]; then
    FORCE=1
  fi
done

echo "==> [generate-dev-tls] Thư mục đích: $TARGET_DIR"

mkdir -p "$TARGET_DIR"
TLS_KEY="$TARGET_DIR/identity-tls.key"
TLS_CRT="$TARGET_DIR/identity-tls.crt"

if [ -e "$TLS_KEY" ] || [ -e "$TLS_CRT" ]; then
  if [ "$FORCE" -ne 1 ]; then
    echo "⚠️  CẢNH BÁO: Đã tồn tại chứng chỉ TLS tại: $TARGET_DIR" >&2
    echo "   - TLS Key: $TLS_KEY" >&2
    echo "   - TLS Crt: $TLS_CRT" >&2
    echo "   Nếu bạn muốn tạo lại chứng chỉ (hạn 30 ngày), hãy chạy với cờ '--force':" >&2
    echo "   $0 --force" >&2
    exit 1
  else
    echo "⚠️  [FORCE]: Ghi đè chứng chỉ TLS theo yêu cầu."
  fi
fi

OLD_UMASK=$(umask)
umask 077

echo "==> Đang tạo chứng chỉ số x509 tự ký (thời hạn 30 ngày)..."
openssl req -x509 -newkey rsa:3072 -nodes -sha256 -days 30 \
 -keyout "$TLS_KEY" -out "$TLS_CRT" \
 -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1'

chmod 600 "$TLS_KEY"
chmod 644 "$TLS_CRT"
umask "$OLD_UMASK"

echo "✅ Tạo chứng chỉ Dev TLS thành công:"
echo "   - TLS Private Key: $TLS_KEY"
echo "   - TLS Certificate: $TLS_CRT"
echo "👉 HƯỚNG DẪN TIẾP THEO: Hãy import file '$TLS_CRT' vào Gateway JVM truststore nếu bật xác thực TLS hai chiều (mTLS) hoặc TLS nội bộ."
