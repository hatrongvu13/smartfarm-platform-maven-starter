#!/usr/bin/env bash
# ==============================================================================
# scripts/generate-dev-keys.sh
# Mục đích: Tạo cặp khóa RSA 3072-bit (private & public key) phục vụ ký số JWT
#          của Identity Service trong môi trường phát triển local (Dev).
# ==============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${1:-$ROOT_DIR/services/identity-service/src/main/resources/.local}"
FORCE=0

for arg in "$@"; do
  if [ "$arg" = "--force" ]; then
    FORCE=1
  fi
done

echo "==> [generate-dev-keys] Thư mục đích: $TARGET_DIR"

mkdir -p "$TARGET_DIR"
PRIV_KEY="$TARGET_DIR/identity-private.pem"
PUB_KEY="$TARGET_DIR/identity-public.pem"

if [ -e "$PRIV_KEY" ] || [ -e "$PUB_KEY" ]; then
  if [ "$FORCE" -ne 1 ]; then
    echo "⚠️  CẢNH BÁO: Đã tồn tại khóa ký số tại: $TARGET_DIR" >&2
    echo "   - Private key: $PRIV_KEY" >&2
    echo "   - Public key:  $PUB_KEY" >&2
    echo "   Hành động ghi đè sẽ làm VÔ HIỆU HÓA toàn bộ token và phiên đăng nhập hiện tại!" >&2
    echo "   Nếu bạn thực sự muốn tạo mới và chấp nhận hệ quả, hãy truyền cờ '--force':" >&2
    echo "   $0 --force" >&2
    exit 1
  else
    echo "⚠️  [FORCE]: Ghi đè cặp khóa hiện tại theo yêu cầu."
  fi
fi

OLD_UMASK=$(umask)
umask 077

echo "==> Đang sinh khóa RSA 3072-bit..."
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$PRIV_KEY"
openssl pkey -in "$PRIV_KEY" -pubout -out "$PUB_KEY"

chmod 600 "$PRIV_KEY"
chmod 644 "$PUB_KEY"
umask "$OLD_UMASK"

echo "✅ Sinh khóa thành công:"
echo "   - Private Key: $PRIV_KEY (Quyền: 600)"
echo "   - Public Key:  $PUB_KEY (Quyền: 644)"
echo "⚠️  LƯU Ý BẢO MẬT: Tuyệt đối không commit các khóa này vào Git repository công khai!"
