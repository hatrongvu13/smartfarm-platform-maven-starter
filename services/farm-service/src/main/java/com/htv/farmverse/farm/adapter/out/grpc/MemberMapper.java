package com.htv.farmverse.farm.adapter.out.grpc;

import com.htv.farmverse.contracts.identity.v1.Member;
import com.htv.farmverse.contracts.identity.v1.MemberStatus;
import com.htv.farmverse.farm.application.MemberDirectory;

/** Biên proto → record của farm. Nơi duy nhất (cùng {@link GrpcMemberDirectory}) import lớp sinh ra của hợp đồng. */
final class MemberMapper {

    private MemberMapper() {
    }

    static MemberDirectory.Member toDomain(Member proto) {
        return new MemberDirectory.Member(proto.getUserId(), proto.getDisplayName(), toDomain(proto.getStatus()));
    }

    /** Giá trị enum lạ (hợp đồng v2 thêm trạng thái mới) coi như không ACTIVE, không làm sập. */
    static MemberDirectory.Status toDomain(MemberStatus status) {
        return switch (status) {
            case MEMBER_STATUS_ACTIVE -> MemberDirectory.Status.ACTIVE;
            case MEMBER_STATUS_SUSPENDED -> MemberDirectory.Status.SUSPENDED;
            default -> MemberDirectory.Status.DISABLED;
        };
    }
}
