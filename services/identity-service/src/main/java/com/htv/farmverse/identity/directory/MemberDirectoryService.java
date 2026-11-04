package com.htv.farmverse.identity.directory;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.htv.farmverse.identity.tenant.Membership;
import com.htv.farmverse.identity.tenant.MembershipRepository;
import com.htv.farmverse.identity.tenant.MembershipStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh bạ thành viên chỉ-đọc cho service khác (gRPC {@code IdentityDirectory}, P2).
 *
 * <p>Trả {@link MemberInfo} (record), không trả entity {@link Membership}: lazy-load không lọt ra adapter, và chỉ có
 * dữ liệu tối thiểu (id, tên hiển thị, trạng thái; không email). Luôn lọc theo tenant: người ở tenant khác là
 * "không tồn tại", không phải "không được phép", để không lộ việc id đó có ở đâu đó hay không.
 */
@Service
public class MemberDirectoryService {

    /** Số id tối đa mỗi lần hỏi theo lô (hợp đồng proto). */
    public static final int MAX_BATCH = 100;

    private final MembershipRepository memberships;

    public MemberDirectoryService(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    public record MemberInfo(String userId, String displayName, MembershipStatus status) {
    }

    @Transactional(readOnly = true)
    public Optional<MemberInfo> get(String tenantId, String userId) {
        return memberships.findByTenantIdAndUserId(tenantId, userId).map(MemberDirectoryService::info);
    }

    /** Bỏ qua id không có trong tenant; giữ thứ tự, bỏ trùng. Quá {@value #MAX_BATCH} id thì lỗi. */
    @Transactional(readOnly = true)
    public List<MemberInfo> batch(String tenantId, Collection<String> userIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(userIds);
        if (ids.size() > MAX_BATCH) {
            throw new IllegalArgumentException("Tối đa " + MAX_BATCH + " user_ids mỗi lần");
        }
        return ids.stream()
                .map(id -> memberships.findByTenantIdAndUserId(tenantId, id))
                .flatMap(Optional::stream)
                .map(MemberDirectoryService::info)
                .toList();
    }

    private static MemberInfo info(Membership membership) {
        return new MemberInfo(membership.getUser().getId(), membership.getUser().getDisplayName(), membership.getStatus());
    }
}
