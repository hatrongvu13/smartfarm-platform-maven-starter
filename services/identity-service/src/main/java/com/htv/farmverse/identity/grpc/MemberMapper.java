package com.htv.farmverse.identity.grpc;

import com.htv.farmverse.contracts.identity.v1.Member;
import com.htv.farmverse.contracts.identity.v1.MemberStatus;
import com.htv.farmverse.identity.directory.MemberDirectoryService.MemberInfo;
import com.htv.farmverse.identity.tenant.MembershipStatus;

/** Biên proto ↔ record của identity. Nơi duy nhất (cùng {@link IdentityDirectoryGrpcService}) import lớp sinh ra. */
final class MemberMapper {

    private MemberMapper() {
    }

    static Member toProto(MemberInfo info) {
        return Member.newBuilder()
                .setUserId(info.userId())
                .setDisplayName(info.displayName() == null ? "" : info.displayName())
                .setStatus(toProto(info.status()))
                .build();
    }

    static MemberStatus toProto(MembershipStatus status) {
        if (status == null) {
            return MemberStatus.MEMBER_STATUS_UNSPECIFIED;
        }
        return switch (status) {
            case ACTIVE -> MemberStatus.MEMBER_STATUS_ACTIVE;
            case SUSPENDED -> MemberStatus.MEMBER_STATUS_SUSPENDED;
            case DISABLED -> MemberStatus.MEMBER_STATUS_DISABLED;
        };
    }
}
