package com.htv.smartfarm.identity.grpc;

import java.time.Instant;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.identity.account.application.AccountService;
import com.htv.smartfarm.identity.grpc.security.GrpcRequestSecurity;
import com.htv.smartfarm.identity.grpc.security.IdentityGrpcAuthorities;
import com.htv.smartfarm.identity.mfa.application.AuthenticatorService;
import com.htv.smartfarm.identity.mfa.application.CredentialAdministrationService;
import com.htv.smartfarm.identity.mfa.application.model.SecurityProfile;
import com.htv.smartfarm.proto.identity.v1.AuthenticatorInfo;
import com.htv.smartfarm.proto.identity.v1.BeginTotpEnrollmentRequest;
import com.htv.smartfarm.proto.identity.v1.BeginTotpEnrollmentResponse;
import com.htv.smartfarm.proto.identity.v1.ChangeOwnPasswordRequest;
import com.htv.smartfarm.proto.identity.v1.ChangeOwnPasswordResponse;
import com.htv.smartfarm.proto.identity.v1.ConfirmTotpEnrollmentRequest;
import com.htv.smartfarm.proto.identity.v1.ConfirmTotpEnrollmentResponse;
import com.htv.smartfarm.proto.identity.v1.DisableOwnMfaRequest;
import com.htv.smartfarm.proto.identity.v1.DisableOwnMfaResponse;
import com.htv.smartfarm.proto.identity.v1.GetSecurityProfileRequest;
import com.htv.smartfarm.proto.identity.v1.GetSecurityProfileResponse;
import com.htv.smartfarm.proto.identity.v1.IdentityCredentialServiceGrpc;
import com.htv.smartfarm.proto.identity.v1.RegenerateRecoveryCodesRequest;
import com.htv.smartfarm.proto.identity.v1.RegenerateRecoveryCodesResponse;
import com.htv.smartfarm.proto.identity.v1.ResetUserMfaRequest;
import com.htv.smartfarm.proto.identity.v1.ResetUserMfaResponse;
import com.htv.smartfarm.proto.identity.v1.SecurityProfileInfo;

import io.grpc.stub.StreamObserver;

import org.springframework.stereotype.Service;

@Service
public class IdentityCredentialGrpcService
        extends IdentityCredentialServiceGrpc
        .IdentityCredentialServiceImplBase {

    private final AuthenticatorService authenticatorService;
    private final CredentialAdministrationService administrationService;
    private final AccountService accountService;
    private final GrpcRequestSecurity requestSecurity;
    private final GrpcExceptionMapper exceptionMapper;

    public IdentityCredentialGrpcService(
            AuthenticatorService authenticatorService,
            CredentialAdministrationService administrationService,
            AccountService accountService,
            GrpcRequestSecurity requestSecurity,
            GrpcExceptionMapper exceptionMapper
    ) {
        this.authenticatorService = authenticatorService;
        this.administrationService = administrationService;
        this.accountService = accountService;
        this.requestSecurity = requestSecurity;
        this.exceptionMapper = exceptionMapper;
    }

    @Override
    public void getSecurityProfile(
            GetSecurityProfileRequest request,
            StreamObserver<GetSecurityProfileResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities.SECURITY_READ
            );
            SecurityProfile profile = authenticatorService.getSecurityProfile(
                    secured.tenantId(),
                    secured.subjectId()
            );
            return GetSecurityProfileResponse.newBuilder()
                    .setSecurityProfile(toProto(profile))
                    .build();
        });
    }

    @Override
    public void beginTotpEnrollment(
            BeginTotpEnrollmentRequest request,
            StreamObserver<BeginTotpEnrollmentResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities.MFA_ENROLL
            );
            var enrollment = authenticatorService.beginTotpEnrollment(
                    secured.subjectId(),
                    request.getDisplayName()
            );
            return BeginTotpEnrollmentResponse.newBuilder()
                    .setAuthenticatorId(enrollment.authenticatorId())
                    .setOtpauthUri(enrollment.otpauthUri())
                    .setExpiresAt(toTimestamp(enrollment.expiresAt()))
                    .build();
        });
    }

    @Override
    public void confirmTotpEnrollment(
            ConfirmTotpEnrollmentRequest request,
            StreamObserver<ConfirmTotpEnrollmentResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            requireText(request.getAuthenticatorId(), "authenticator_id");
            requireText(request.getCode(), "code");
            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities.MFA_ENROLL
            );
            var confirmation = authenticatorService.confirmTotpEnrollment(
                    secured.subjectId(),
                    request.getAuthenticatorId(),
                    request.getCode()
            );
            return ConfirmTotpEnrollmentResponse.newBuilder()
                    .setAuthenticatorId(confirmation.authenticatorId())
                    .addAllRecoveryCodes(confirmation.recoveryCodes())
                    .build();
        });
    }

    @Override
    public void disableOwnMfa(
            DisableOwnMfaRequest request,
            StreamObserver<DisableOwnMfaResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            requireText(request.getAuthenticatorId(), "authenticator_id");
            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities.MFA_DISABLE
            );
            authenticatorService.disableAuthenticator(
                    secured.tenantId(),
                    secured.subjectId(),
                    request.getAuthenticatorId()
            );
            return DisableOwnMfaResponse.newBuilder()
                    .setDisabled(true)
                    .build();
        });
    }

    @Override
    public void regenerateRecoveryCodes(
            RegenerateRecoveryCodesRequest request,
            StreamObserver<RegenerateRecoveryCodesResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            requireText(request.getAuthenticatorId(), "authenticator_id");
            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities.MFA_RECOVERY_REGENERATE
            );
            var codes = authenticatorService.regenerateRecoveryCodes(
                    secured.subjectId(),
                    request.getAuthenticatorId()
            );
            return RegenerateRecoveryCodesResponse.newBuilder()
                    .addAllRecoveryCodes(codes)
                    .build();
        });
    }

    @Override
    public void resetUserMfa(
            ResetUserMfaRequest request,
            StreamObserver<ResetUserMfaResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            requireText(request.getSubjectId(), "subject_id");
            requestSecurity.authorizeTenantAdministration(
                    request.getContext(),
                    IdentityGrpcAuthorities.USER_MFA_RESET
            );
            administrationService.resetUserMfa(request.getSubjectId());
            return ResetUserMfaResponse.newBuilder()
                    .setReset(true)
                    .build();
        });
    }

    @Override
    public void changeOwnPassword(
            ChangeOwnPasswordRequest request,
            StreamObserver<ChangeOwnPasswordResponse> responseObserver
    ) {
        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            requireText(request.getCurrentPassword(), "current_password");
            requireText(request.getNewPassword(), "new_password");
            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities.USER_CREDENTIAL_RESET
            );
            accountService.changePassword(
                    secured.subjectId(),
                    request.getCurrentPassword(),
                    request.getNewPassword()
            );
            return ChangeOwnPasswordResponse.newBuilder()
                    .setChanged(true)
                    .build();
        });
    }

    private SecurityProfileInfo toProto(SecurityProfile source) {
        SecurityProfileInfo.Builder result = SecurityProfileInfo.newBuilder()
                .setSubjectId(source.userId())
                .setTotpRequired(source.totpRequired())
                .setTotpEnabled(source.totpEnabled());

        source.authenticators().stream()
                .map(this::toProto)
                .forEach(result::addAuthenticators);

        return result.build();
    }

    private AuthenticatorInfo toProto(
            SecurityProfile.AuthenticatorSummary source
    ) {
        return AuthenticatorInfo.newBuilder()
                .setAuthenticatorId(source.id())
                .setType(source.type())
                .setStatus(source.status())
                .setDisplayName(source.displayName())
                .build();
    }

    private Timestamp toTimestamp(Instant source) {
        return Timestamp.newBuilder()
                .setSeconds(source.getEpochSecond())
                .setNanos(source.getNano())
                .build();
    }

    private void requireContext(boolean hasContext) {
        if (!hasContext) {
            throw new IllegalArgumentException(
                    "request context is required"
            );
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }
}
