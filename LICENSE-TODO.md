# LICENSE — Requires owner decision

> ⚠️ **CHƯA XÁC ĐỊNH LICENSE.** Repository hiện **không** có file `LICENSE` và chưa khai báo rõ quyền sở hữu/chính sách phát hành.

Theo nguyên tắc an toàn, agent **không tự chọn** loại license. Việc chọn license là quyết định của chủ sở hữu dự án.

## Cần project owner quyết định
1. Dự án là **private/proprietary** hay **open source**?
2. Nếu open source: license nào? (MIT / Apache-2.0 / BSD-3 / GPL...). Apache-2.0 thường phù hợp cho dự án Java doanh nghiệp vì có điều khoản patent + NOTICE.
3. Chủ sở hữu bản quyền (copyright holder) là ai? (cá nhân / tổ chức `com.htv`?)

## Khi đã quyết định
- Thêm file `LICENSE` ở root với nội dung license chuẩn.
- Nếu Apache-2.0: thêm `NOTICE` + header license cho source nếu cần.
- Chạy kiểm tra license của dependency (Maven) trước khi tạo `THIRD_PARTY_LICENSES.md` — **không** tuyên bố dự án open source khi chưa có bằng chứng.

## Trạng thái hiện tại
- `LICENSE`: ❌ chưa có
- `NOTICE`: ❌ chưa có
- `THIRD_PARTY_LICENSES.md`: ❌ chưa có (cần quét dependency license trước)

**→ DECISION REQUIRED: project owner.**
