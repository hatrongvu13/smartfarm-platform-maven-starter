package com.htv.farmverse.farm.application;

import java.util.List;
import java.util.Optional;

/**
 * Port ra ngoài: hỏi identity về thành viên (hexagonal). Use case chỉ biết interface này; cài đặt là adapter gRPC.
 * Tenant đến từ ngữ cảnh bảo mật hiện tại (token), adapter tự lo, use case không truyền.
 */
public interface MemberDirectory {

    /** Trạng thái thành viên như use case cần. */
    enum Status { ACTIVE, SUSPENDED, DISABLED }

    record Member(String userId, String displayName, Status status) {
    }

    /** Thành viên trong tenant hiện tại, rỗng nếu không có. */
    Optional<Member> find(String userId);

    /** Tên hiển thị cho các id (bỏ qua id không thuộc tenant hiện tại). Khi identity lỗi: ném {@link DirectoryUnavailable}. */
    List<Member> findAll(List<String> userIds);

    /** Identity không trả lời (sập, quá hạn). Use case chọn từ chối (ghi) hay giảm cấp (đọc). */
    class DirectoryUnavailable extends RuntimeException {
        public DirectoryUnavailable(Throwable cause) {
            super("Danh bạ thành viên tạm thời không gọi được", cause);
        }
    }
}
