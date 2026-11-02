#!/usr/bin/env bash
# ==============================================================================
# scripts/rotate-signing-key.sh <key-id-moi> [thu-muc]
# Mục đích: sinh cặp khóa RSA 3072-bit MỚI để xoay khóa ký JWT của identity.
#           Không đụng vào khóa đang dùng, không ghi đè file có sẵn.
# Xem: docs/operations/key-rotation.md
# ==============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEY_ID="${1:-}"
TARGET_DIR="${2:-$ROOT_DIR/services/identity-service/src/main/resources/.local}"

if [ -z "$KEY_ID" ]; then
  echo "Cách dùng: $0 <key-id-moi> [thu-muc]   ví dụ: $0 key-2026-11" >&2
  exit 1
fi
if ! [[ "$KEY_ID" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "key-id chỉ gồm chữ, số, '.', '_', '-'." >&2
  exit 1
fi

PRIV_KEY="$TARGET_DIR/$KEY_ID-private.pem"
PUB_KEY="$TARGET_DIR/$KEY_ID-public.pem"

if [ -e "$PRIV_KEY" ] || [ -e "$PUB_KEY" ]; then
  echo "Đã có khóa '$KEY_ID' tại $TARGET_DIR. Chọn key-id khác." >&2
  exit 1
fi

mkdir -p "$TARGET_DIR"
OLD_UMASK=$(umask)
umask 077
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$PRIV_KEY"
openssl pkey -in "$PRIV_KEY" -pubout -out "$PUB_KEY"
chmod 600 "$PRIV_KEY"
chmod 644 "$PUB_KEY"
umask "$OLD_UMASK"

cat <<EOF
Đã sinh khóa mới '$KEY_ID':
  - Private: $PRIV_KEY (600)
  - Public:  $PUB_KEY

Các bước tiếp (chi tiết: docs/operations/key-rotation.md):
  1. Khóa đang ký hiện tại chuyển thành 'retired' (chỉ giữ file public của nó).
  2. Đặt khóa mới làm khóa ký:
       IDENTITY_KEY_ID=$KEY_ID
       IDENTITY_PRIVATE_KEY_PATH=$PRIV_KEY
       IDENTITY_PUBLIC_KEY_PATH=$PUB_KEY
  3. Khai khóa cũ (thay <...>):
       FARMVERSE_IDENTITY_SIGNINGKEY_RETIRED_0_KEYID=<key-id-cu>
       FARMVERSE_IDENTITY_SIGNINGKEY_RETIRED_0_PUBLICKEY=<duong-dan-public-cu>
  4. Khởi động lại identity, kiểm tra /oauth2/jwks có đủ 2 kid.
  5. Sau khi token cuối ký bằng khóa cũ hết hạn, gỡ 'retired' rồi khởi động lại.
Tuyệt đối không commit file .pem vào Git.
EOF
