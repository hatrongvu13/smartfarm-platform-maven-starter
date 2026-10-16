#!/usr/bin/env bash
# ==============================================================================
# scripts/generate-secret.sh
# Mục đích: Sinh chuỗi ngẫu nhiên chuẩn mật mã (cryptographically secure random
#          string, base64 encoded) với độ dài byte tùy chỉnh (mặc định 32 bytes).
#          Dùng để tạo secret key cho HMAC SFM1, AES-256 MFA Encryption Key,
#          hoặc Session Cookies Secret.
# ==============================================================================
set -euo pipefail

BYTES="${1:-32}"
openssl rand -base64 "$BYTES"
