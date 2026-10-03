#!/usr/bin/env bash

set -euo pipefail

# Các cổng cần forward (SSH, MQTT, PostgreSQL, Redis)
PORTS=(
    8022
    1883
    5432
    6379
)

# 1. Kiểm tra công cụ ADB
if ! command -v adb >/dev/null 2>&1; then
    echo "❌ LỖI: Không tìm thấy 'adb'. Hãy cài đặt Android platform-tools." >&2
    exit 1
fi

# 2. Xử lý trường hợp có nhiều thiết bị ADB
DEVICES=$(adb devices | grep -v "List of devices attached" | grep -v "^$" | awk '{print $1}')
DEVICE_COUNT=$(echo "$DEVICES" | wc -w | tr -d ' ')

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "❌ LỖI: Không tìm thấy thiết bị Android nào kết nối." >&2
    echo "Kiểm tra lại cáp kết nối hoặc Chế độ bật USB Debugging." >&2
    exit 1
elif [ "$DEVICE_COUNT" -gt 1 ]; then
    echo "⚠️  CẢNH BÁO: Phát hiện nhiều thiết bị kết nối. Đang chọn thiết bị đầu tiên:"
    DEVICE_ID=$(echo "$DEVICES" | head -n 1)
    echo "👉 Thiết bị chọn: $DEVICE_ID"
    ADB_CMD=("adb" "-s" "$DEVICE_ID")
else
    ADB_CMD=("adb")
fi

echo "=========================================="
echo "Kết nối thiết bị: "$("${ADB_CMD[@]}" get-serialno 2>/dev/null || echo "OK")""
echo "=========================================="

# Hàm dọn dẹp khi thoát
cleanup() {
    echo -e "\n🧹 Đang gỡ bỏ các Port Forwarding..."
    for port in "${PORTS[@]}"; do
        "${ADB_CMD[@]}" forward --remove "tcp:${port}" 2>/dev/null || true
    done
    echo "✅ Đã dọn dẹp xong."
}

trap cleanup EXIT INT TERM

# 3. Dọn dẹp port cũ
echo "Gỡ bỏ cấu hình forward cũ (nếu có)..."
for port in "${PORTS[@]}"; do
    "${ADB_CMD[@]}" forward --remove "tcp:${port}" 2>/dev/null || true
done

# 4. Thiết lập Port Forwarding mới
echo -e "\nĐang tạo Port Forwarding mới..."
for port in "${PORTS[@]}"; do
    if "${ADB_CMD[@]}" forward "tcp:${port}" "tcp:${port}" >/dev/null 2>&1; then
        echo "  🟢 localhost:${port} -> phone:${port}"
    else
        echo "  🔴 Thất bại khi forward port ${port} (có thể port trên máy Mac đã bị chiếm dụng)"
    fi
done

echo -e "\n=========================================="
echo "Danh sách Port Forwarding đang hoạt động:"
echo "=========================================="
"${ADB_CMD[@]}" forward --list

echo -e "\n🚀 Đang duy trì kết nối. Nhấn Ctrl+C để dừng."

# Chờ tín hiệu dừng từ người dùng
cat < /dev/null