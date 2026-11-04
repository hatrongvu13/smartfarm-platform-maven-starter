package com.htv.farmverse.identity.grpc;

import java.util.List;
import java.util.regex.Pattern;

import com.htv.farmverse.contracts.identity.v1.BatchGetMembersRequest;
import com.htv.farmverse.contracts.identity.v1.BatchGetMembersResponse;
import com.htv.farmverse.contracts.identity.v1.GetMemberRequest;
import com.htv.farmverse.contracts.identity.v1.IdentityDirectoryGrpc;
import com.htv.farmverse.contracts.identity.v1.Member;
import com.htv.farmverse.identity.directory.MemberDirectoryService;
import com.htv.farmverse.service.security.FarmVerseClaims;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Server gRPC {@code farmverse.identity.v1.IdentityDirectory} (P2).
 *
 * <p>Ngữ cảnh tenant theo D3-B: service gọi (farm) chuyển tiếp <b>access token của người dùng</b> trong metadata
 * {@code authorization: Bearer ...}. {@link GrpcSecurityConfiguration} kiểm token (chữ ký, iss, aud
 * {@code identity-service}, hạn) trước khi tới đây. Tenant lấy từ claim {@code tenant_id} của token, không tin
 * {@code tenant_id} trong request: nếu request có gửi mà khác token thì {@code PERMISSION_DENIED}. Vì vậy farm bị chiếm
 * cũng chỉ hỏi được tenant của người dùng đang có token hợp lệ, không hỏi được tenant tùy ý.
 *
 * <p>Token của máy ({@code client_credentials}, không có người dùng và tenant) bị từ chối. Người khác tenant hay id
 * không tồn tại đều là {@code NOT_FOUND}. Không log id hay tên người dùng.
 */
@Service
public class IdentityDirectoryGrpcService extends IdentityDirectoryGrpc.IdentityDirectoryImplBase {

    /** Id do identity cấp là UUID dạng chuỗi. */
    private static final Pattern ID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final MemberDirectoryService directory;

    public IdentityDirectoryGrpcService(MemberDirectoryService directory) {
        this.directory = directory;
    }

    @Override
    public void getMember(GetMemberRequest request, StreamObserver<Member> response) {
        try {
            String tenantId = callerTenant(request.getTenantId());
            String userId = validId(request.getUserId(), "user_id");
            Member member = directory.get(tenantId, userId).map(MemberMapper::toProto)
                    .orElseThrow(() -> Status.NOT_FOUND.withDescription("member not found").asRuntimeException());
            response.onNext(member);
            response.onCompleted();
        } catch (StatusRuntimeException e) {
            response.onError(e);
        }
    }

    @Override
    public void batchGetMembers(BatchGetMembersRequest request, StreamObserver<BatchGetMembersResponse> response) {
        try {
            String tenantId = callerTenant(request.getTenantId());
            if (request.getUserIdsCount() > MemberDirectoryService.MAX_BATCH) {
                throw Status.INVALID_ARGUMENT.withDescription("at most " + MemberDirectoryService.MAX_BATCH + " user_ids")
                        .asRuntimeException();
            }
            List<String> ids = request.getUserIdsList().stream().map(id -> validId(id, "user_ids")).toList();
            response.onNext(BatchGetMembersResponse.newBuilder()
                    .addAllMembers(directory.batch(tenantId, ids).stream().map(MemberMapper::toProto).toList())
                    .build());
            response.onCompleted();
        } catch (StatusRuntimeException e) {
            response.onError(e);
        }
    }

    /** Tenant của người dùng trong token đã kiểm. */
    private static String callerTenant(String requested) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw Status.UNAUTHENTICATED.withDescription("user access token required").asRuntimeException();
        }
        String tenantId = token.getToken().getClaimAsString(FarmVerseClaims.TENANT_ID);
        if (!"user".equals(token.getToken().getClaimAsString(FarmVerseClaims.TOKEN_TYPE)) || tenantId == null || tenantId.isBlank()) {
            throw Status.PERMISSION_DENIED.withDescription("user access token with tenant required").asRuntimeException();
        }
        if (!requested.isEmpty() && !requested.equals(tenantId)) {
            throw Status.PERMISSION_DENIED.withDescription("tenant_id does not match the access token").asRuntimeException();
        }
        return tenantId;
    }

    private static String validId(String value, String field) {
        if (value == null || !ID.matcher(value).matches()) {
            throw Status.INVALID_ARGUMENT.withDescription(field + " must be an id").asRuntimeException();
        }
        return value;
    }
}
