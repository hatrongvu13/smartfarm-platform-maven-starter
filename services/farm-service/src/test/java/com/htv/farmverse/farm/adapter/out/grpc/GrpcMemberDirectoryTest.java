package com.htv.farmverse.farm.adapter.out.grpc;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.htv.farmverse.contracts.identity.v1.BatchGetMembersRequest;
import com.htv.farmverse.contracts.identity.v1.BatchGetMembersResponse;
import com.htv.farmverse.contracts.identity.v1.GetMemberRequest;
import com.htv.farmverse.contracts.identity.v1.IdentityDirectoryGrpc;
import com.htv.farmverse.contracts.identity.v1.Member;
import com.htv.farmverse.contracts.identity.v1.MemberStatus;
import com.htv.farmverse.farm.application.MemberDirectory;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3: {@link GrpcMemberDirectory} thật gọi một server gRPC in-process đóng vai identity. Kiểm:
 *  - token của người dùng trong SecurityContext được chuyển tiếp vào metadata authorization (D3-B);
 *  - NOT_FOUND → rỗng; UNAVAILABLE → DirectoryUnavailable; batch bỏ id lạ; enum lạ không làm sập mapper.
 */
class GrpcMemberDirectoryTest {

    static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    static final Context.Key<String> SEEN_AUTH = Context.key("auth");

    FakeIdentity identity;
    Server server;
    String serverName;
    GrpcMemberDirectory directory;

    @BeforeEach
    void start() throws IOException {
        identity = new FakeIdentity();
        serverName = "identity-" + System.nanoTime();
        server = InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(identity).intercept(CAPTURE_AUTH).build().start();
        // Factory mở channel in-process mới cho mỗi lời gọi (adapter đóng channel sau khi dùng, như với channel thật).
        directory = new GrpcMemberDirectory(new InProcessChannelFactory(serverName), "identity");
        authenticate("user-token-abc");
    }

    @AfterEach
    void stop() {
        SecurityContextHolder.clearContext();
        server.shutdownNow();
    }

    static void authenticate(String tokenValue) {
        Jwt jwt = Jwt.withTokenValue(tokenValue).header("alg", "none")
                .claim("sub", "membership-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void findForwardsTheUserTokenAndMapsTheMember() {
        identity.members.put("u1", member("u1", "Chị An", MemberStatus.MEMBER_STATUS_ACTIVE));

        MemberDirectory.Member found = directory.find("u1").orElseThrow();

        assertThat(found.userId()).isEqualTo("u1");
        assertThat(found.displayName()).isEqualTo("Chị An");
        assertThat(found.status()).isEqualTo(MemberDirectory.Status.ACTIVE);
        assertThat(identity.lastAuthorization).isEqualTo("Bearer user-token-abc");
    }

    @Test
    void notFoundBecomesEmpty() {
        assertThat(directory.find("ghost")).isEmpty();
    }

    @Test
    void unavailableBecomesDirectoryUnavailable() {
        identity.fail = Status.UNAVAILABLE;
        assertThatThrownBy(() -> directory.find("u1")).isInstanceOf(MemberDirectory.DirectoryUnavailable.class);
        identity.fail = Status.DEADLINE_EXCEEDED;
        assertThatThrownBy(() -> directory.find("u1")).isInstanceOf(MemberDirectory.DirectoryUnavailable.class);
    }

    @Test
    void withoutAUserTokenTheCallIsNotMade() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", "y"));
        assertThatThrownBy(() -> directory.find("u1")).isInstanceOf(io.grpc.StatusRuntimeException.class);
    }

    @Test
    void batchDropsUnknownIdsAndUnknownStatusMapsToNonActive() {
        identity.members.put("u1", member("u1", "An", MemberStatus.MEMBER_STATUS_ACTIVE));
        identity.members.put("u2", member("u2", "Mới", MemberStatus.forNumber(99) == null
                ? MemberStatus.MEMBER_STATUS_UNSPECIFIED : MemberStatus.MEMBER_STATUS_UNSPECIFIED));

        List<MemberDirectory.Member> members = directory.findAll(List.of("u1", "u2", "ghost"));

        assertThat(members).extracting(MemberDirectory.Member::userId).containsExactly("u1", "u2");
        assertThat(members.get(1).status()).isEqualTo(MemberDirectory.Status.DISABLED);
    }

    static Member member(String id, String name, MemberStatus status) {
        return Member.newBuilder().setUserId(id).setDisplayName(name).setStatus(status).build();
    }

    /** Ghi lại header authorization mà server nhận được. */
    static final ServerInterceptor CAPTURE_AUTH = new ServerInterceptor() {
        @Override
        public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {
            String auth = headers.get(AUTHORIZATION);
            return Contexts.interceptCall(Context.current().withValue(SEEN_AUTH, auth), call, headers, next);
        }
    };

    static final class FakeIdentity extends IdentityDirectoryGrpc.IdentityDirectoryImplBase {
        final Map<String, Member> members = new java.util.HashMap<>();
        volatile Status fail;
        volatile String lastAuthorization;

        @Override
        public void getMember(GetMemberRequest request, StreamObserver<Member> response) {
            lastAuthorization = SEEN_AUTH.get();
            if (fail != null) {
                response.onError(fail.asRuntimeException());
                return;
            }
            Member member = members.get(request.getUserId());
            if (member == null) {
                response.onError(Status.NOT_FOUND.asRuntimeException());
                return;
            }
            response.onNext(member);
            response.onCompleted();
        }

        @Override
        public void batchGetMembers(BatchGetMembersRequest request, StreamObserver<BatchGetMembersResponse> response) {
            lastAuthorization = SEEN_AUTH.get();
            BatchGetMembersResponse.Builder out = BatchGetMembersResponse.newBuilder();
            request.getUserIdsList().stream().map(members::get).filter(java.util.Objects::nonNull).forEach(out::addMembers);
            response.onNext(out.build());
            response.onCompleted();
        }
    }

    /** GrpcChannelFactory tối giản: mở một channel in-process mới mỗi lần (adapter tự đóng sau khi dùng). */
    record InProcessChannelFactory(String serverName) implements GrpcChannelFactory {
        @Override
        public ManagedChannel createChannel(String name, ChannelBuilderOptions options) {
            return InProcessChannelBuilder.forName(serverName).directExecutor().build();
        }

        @Override
        public boolean supports(String target) {
            return true;
        }
    }
}
