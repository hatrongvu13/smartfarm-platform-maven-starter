#!/usr/bin/env bash
#
# generate-dev-mtls.sh — CA + chứng chỉ mTLS cho từng service (CHỈ DÙNG DEV).
#
#   ./scripts/generate-dev-mtls.sh [thư-mục-ra] [--force]
#
# Mặc định ghi vào .local/mtls/ ở gốc repo (đã gitignore):
#   ca.crt / ca.key                  CA dev, hạn 30 ngày
#   <service>.crt / <service>.key    cho identity, farm, gateway
#
# Mỗi cert dùng được cả hai chiều (serverAuth + clientAuth) và mang danh tính trong SAN:
#   URI:spiffe://farmverse/<service>, DNS:localhost, IP:127.0.0.1
# Service bên nhận kiểm SAN URI để biết AI đang gọi (xem ServiceIdentityFilter).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/.local/mtls"
FORCE=false
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=true ;;
    *) OUT="$arg" ;;
  esac
done
SERVICES=(identity farm gateway)
DAYS=30

command -v openssl >/dev/null || { echo "ERROR: cần openssl" >&2; exit 1; }
if [[ -f "$OUT/ca.crt" && "$FORCE" != true ]]; then
  echo "Đã có $OUT/ca.crt — dùng --force để tạo lại (cert cũ sẽ mất hiệu lực)." >&2
  exit 1
fi

mkdir -p "$OUT"
umask 077
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> CA dev"
openssl req -x509 -newkey rsa:3072 -sha256 -nodes -days "$DAYS" \
  -keyout "$OUT/ca.key" -out "$OUT/ca.crt" \
  -subj "/O=FarmVerse Dev/CN=FarmVerse Dev CA" \
  -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
  -addext "keyUsage=critical,keyCertSign,cRLSign" 2>/dev/null

for svc in "${SERVICES[@]}"; do
  echo "==> $svc"
  cat > "$WORK/$svc.ext" <<EOF
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth,clientAuth
subjectAltName=URI:spiffe://farmverse/$svc,DNS:localhost,IP:127.0.0.1
EOF
  # PKCS#8 (BEGIN PRIVATE KEY) để Spring Boot SSL bundle đọc trực tiếp
  openssl req -newkey rsa:2048 -sha256 -nodes \
    -keyout "$OUT/$svc.key" -out "$WORK/$svc.csr" \
    -subj "/O=FarmVerse Dev/CN=$svc" 2>/dev/null
  openssl x509 -req -sha256 -days "$DAYS" -in "$WORK/$svc.csr" \
    -CA "$OUT/ca.crt" -CAkey "$OUT/ca.key" -CAcreateserial -CAserial "$WORK/ca.srl" \
    -extfile "$WORK/$svc.ext" -out "$OUT/$svc.crt" 2>/dev/null
done

chmod 600 "$OUT"/*.key
chmod 644 "$OUT"/*.crt
echo "==> Xong: $OUT"
echo "    Trình duyệt sẽ báo cert lạ ở https://localhost:8092 (trang login) cho tới khi tin ca.crt:"
echo "    macOS: sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain $OUT/ca.crt"
