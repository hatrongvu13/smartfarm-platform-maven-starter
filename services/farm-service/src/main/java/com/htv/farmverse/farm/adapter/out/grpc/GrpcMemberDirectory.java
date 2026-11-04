package com.htv.farmverse.farm.adapter.out.grpc;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import com.htv.farmverse.contracts.identity.v1.BatchGetMembersRequest;
import com.htv.farmverse.contracts.identity.v1.GetMemberRequest;
import com.htv.farmverse.contracts.identity.v1.IdentityDirectoryGrpc;
import com.htv.farmverse.farm.application.MemberDirectory;

import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Cài đặt {@link MemberDirectory} bằng gRPC tới identity (P3, D3-B).
 *
 * <p>Chuyển tiếp access token của người dùng đang gọi (lấy từ {@link SecurityContextHolder}) vào metadata
 * {@code authorization}: identity dùng nó để biết tenant và quyền. mTLS (cert của farm) do channel lo theo
 * {@code spring.grpc.client.channel.identity.ssl}. Deadline mặc định cũng cấu hình ở channel.
 *
 * <p>{@code NOT_FOUND} → rỗng (người không thuộc tenant). {@code UNAVAILABLE}/{@code DEADLINE_EXCEEDED} →
 * {@link MemberDirectory.DirectoryUnavailable} để use case từ chối (ghi) hay giảm cấp (đọc).
 */
@Component
public class GrpcMemberDirectory implements MemberDirectory {

    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final GrpcChannelFactory channels;
    private final String channelName;

    public GrpcMemberDirectory(GrpcChannelFactory channels,
                               @Value("${farmverse.grpc.identity-channel:identity}") String channelName) {
        this.channels = channels;
        this.channelName = channelName;
    }

    @Override
    public Optional<Member> find(String userId) {
        ManagedChannel channel = channels.createChannel(channelName);
        try {
            var member = stub(channel).getMember(GetMemberRequest.newBuilder().setUserId(userId).build());
            return Optional.of(MemberMapper.toDomain(member));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw translate(e);
        } finally {
            channel.shutdown();
        }
    }

    @Override
    public List<Member> findAll(List<String> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        ManagedChannel channel = channels.createChannel(channelName);
        try {
            return stub(channel).batchGetMembers(BatchGetMembersRequest.newBuilder().addAllUserIds(userIds).build())
                    .getMembersList().stream().map(MemberMapper::toDomain).toList();
        } catch (StatusRuntimeException e) {
            throw translate(e);
        } finally {
            channel.shutdown();
        }
    }

    private IdentityDirectoryGrpc.IdentityDirectoryBlockingStub stub(ManagedChannel channel) {
        return IdentityDirectoryGrpc.newBlockingStub(channel).withCallCredentials(forwardedToken());
    }

    private static RuntimeException translate(StatusRuntimeException e) {
        io.grpc.Status.Code code = e.getStatus().getCode();
        if (code == io.grpc.Status.Code.UNAVAILABLE || code == io.grpc.Status.Code.DEADLINE_EXCEEDED) {
            return new DirectoryUnavailable(e);
        }
        return e;   // INVALID_ARGUMENT, PERMISSION_DENIED... là lỗi lập trình/cấu hình, không nuốt
    }

    /** Access token của người dùng hiện tại, đưa vào metadata authorization. */
    private static CallCredentials forwardedToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw io.grpc.Status.UNAUTHENTICATED.withDescription("no user token to forward").asRuntimeException();
        }
        String token = jwt.getToken().getTokenValue();
        return new CallCredentials() {
            @Override
            public void applyRequestMetadata(RequestInfo info, Executor executor, MetadataApplier applier) {
                Metadata headers = new Metadata();
                headers.put(AUTHORIZATION, "Bearer " + token);
                applier.apply(headers);
            }
        };
    }
}
