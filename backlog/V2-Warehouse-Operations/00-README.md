# V2 — Practical Warehouse Operations

## Mục tiêu

Biến kho thành không gian vật lý có thể quản lý chính xác bằng StorageLocation.

Không cần 3D.

Không cần camera.

## Mô hình

Warehouse
→ Area
→ Rack
→ Level
→ Bin
→ StorageLocation

Ví dụ:

A 00 02 04 10

A = rack
00 = level
02 = bin
04 = max levels
10 = max bins/level

QR chỉ là phương thức resolve. StorageLocationId mới là identity nghiệp vụ.
