package com.htv.smartfarm.identity.grpc;

import java.time.Instant;

import com.google.protobuf.Timestamp;
import com.htv.smartfarm.identity.account.application.AccountService;
import com.htv.smartfarm.identity.grpc.security.GrpcRequestSecurity;
import com.htv.smartfarm.identity.grpc.security.IdentityGrpcAuthorities;
import com.htv.smartfarm.identity.mfa.application.AuthenticatorService;
import com.htv.smartfarm.identity.mfa.application
        .CredentialAdministrationService;
import com.htv.smartfarm.identity.mfa.application.model.SecurityProfile;
import com.htv.smartfarm.proto.identity.v1.AuthenticatorInfo;
import com.htv.smartfarm.proto.identity.v1
        .BeginTotpEnrollmentRequest;
import com.htv.smartfarm.proto.identity.v1
        .BeginTotpEnrollmentResponse;
import com.htv.smartfarm.proto.identity.v1.ChangeOwnPasswordRequest;
import com.htv.smartfarm.proto.identity.v1.ChangeOwnPasswordResponse;
import com.htv.smartfarm.proto.identity.v1
        .ConfirmTotpEnrollmentRequest;
import com.htv.smartfarm.proto.identity.v1
        .ConfirmTotpEnrollmentResponse;
import com.htv.smartfarm.proto.identity.v1.DisableOwnMfaRequest;
import com.htv.smartfarm.proto.identity.v1.DisableOwnMfaResponse;
import com.htv.smartfarm.proto.identity.v1.GetSecurityProfileRequest;
import com.htv.smartfarm.proto.identity.v1.GetSecurityProfileResponse;
import com.htv.smartfarm.proto.identity.v1
        .IdentityCredentialServiceGrpc;
import com.htv.smartfarm.proto.identity.v1
        .RegenerateRecoveryCodesRequest;
import com.htv.smartfarm.proto.identity.v1
        .RegenerateRecoveryCodesResponse;
import com.htv.smartfarm.proto.identity.v1.ResetUserMfaRequest;
import com.htv.smartfarm.proto.identity.v1.ResetUserMfaResponse;
import com.htv.smartfarm.proto.identity.v1.SecurityProfileInfo;

import io.grpc.stub.StreamObserver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@GrpcService
@RequiredArgsConstructor
public class IdentityCredentialGrpcService
        extends IdentityCredentialServiceGrpc
        .IdentityCredentialServiceImplBase {

    private final AuthenticatorService authenticatorService;
    private final CredentialAdministrationService administrationService;
    private final AccountService accountService;
    private final GrpcRequestSecurity requestSecurity;
    private final GrpcExceptionMapper exceptionMapper;

    @Override
    public void getSecurityProfile(
            GetSecurityProfileRequest request,
            StreamObserver<GetSecurityProfileResponse>
                    responseObserver
    ) {
        log.debug(
                "Received get security profile request: subjectId={}",
                request.getSubjectId()
        );

        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());

            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities.SECURITY_READ
            );

            log.debug(
                    "Get security profile request authorized: "
                            + "tenantId={}, subjectId={}",
                    secured.tenantId(),
                    secured.subjectId()
            );

            SecurityProfile profile =
                    authenticatorService.getSecurityProfile(
                            secured.tenantId(),
                            secured.subjectId()
                    );

            log.debug(
                    "Security profile retrieved successfully: "
                            + "tenantId={}, subjectId={}, "
                            + "authenticatorCount={}",
                    secured.tenantId(),
                    secured.subjectId(),
                    profile.authenticators().size()
            );

            return GetSecurityProfileResponse.newBuilder()
                    .setSecurityProfile(toProto(profile))
                    .build();
        });
    }

    @Override
    public void beginTotpEnrollment(
            BeginTotpEnrollmentRequest request,
            StreamObserver<BeginTotpEnrollmentResponse>
                    responseObserver
    ) {
        log.info(
                "Received begin TOTP enrollment request: subjectId={}",
                request.getSubjectId()
        );

        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());

            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities.MFA_ENROLL
            );

            log.debug(
                    "Begin TOTP enrollment request authorized: "
                            + "tenantId={}, subjectId={}, "
                            + "correlationId={}",
                    secured.tenantId(),
                    secured.subjectId(),
                    request.getContext().getCorrelationId()
            );

            var enrollment =
                    authenticatorService.beginTotpEnrollment(
                            secured.subjectId(),
                            request.getDisplayName()
                    );

            log.info(
                    "TOTP enrollment initiated successfully: "
                            + "tenantId={}, subjectId={}, "
                            + "authenticatorId={}, correlationId={}",
                    secured.tenantId(),
                    secured.subjectId(),
                    enrollment.authenticatorId(),
                    request.getContext().getCorrelationId()
            );

            return BeginTotpEnrollmentResponse.newBuilder()
                    .setAuthenticatorId(
                            enrollment.authenticatorId()
                    )
                    .setOtpauthUri(enrollment.otpauthUri())
                    .setExpiresAt(
                            toTimestamp(enrollment.expiresAt())
                    )
                    .build();
        });
    }

    @Override
    public void confirmTotpEnrollment(
            ConfirmTotpEnrollmentRequest request,
            StreamObserver<ConfirmTotpEnrollmentResponse>
                    responseObserver
    ) {
        log.info(
                "Received confirm TOTP enrollment request: "
                        + "subjectId={}, authenticatorId={}",
                request.getSubjectId(),
                request.getAuthenticatorId()
        );

        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            requireText(
                    request.getAuthenticatorId(),
                    "authenticator_id"
            );
            requireText(request.getCode(), "code");

            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities.MFA_ENROLL
            );

            log.debug(
                    "Confirm TOTP enrollment request authorized: "
                            + "tenantId={}, subjectId={}, "
                            + "authenticatorId={}, correlationId={}",
                    secured.tenantId(),
                    secured.subjectId(),
                    request.getAuthenticatorId(),
                    request.getContext().getCorrelationId()
            );

            var confirmation =
                    authenticatorService.confirmTotpEnrollment(
                            secured.subjectId(),
                            request.getAuthenticatorId(),
                            request.getCode()
                    );

            log.info(
                    "TOTP enrollment confirmed successfully: "
                            + "tenantId={}, subjectId={}, "
                            + "authenticatorId={}, correlationId={}",
                    secured.tenantId(),
                    secured.subjectId(),
                    confirmation.authenticatorId(),
                    request.getContext().getCorrelationId()
            );

            return ConfirmTotpEnrollmentResponse.newBuilder()
                    .setAuthenticatorId(
                            confirmation.authenticatorId()
                    )
                    .addAllRecoveryCodes(
                            confirmation.recoveryCodes()
                    )
                    .build();
        });
    }

    @Override
    public void disableOwnMfa(
            DisableOwnMfaRequest request,
            StreamObserver<DisableOwnMfaResponse>
                    responseObserver
    ) {
        log.info(
                "Received disable MFA request: "
                        + "subjectId={}, authenticatorId={}",
                request.getSubjectId(),
                request.getAuthenticatorId()
        );

        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            requireText(
                    request.getAuthenticatorId(),
                    "authenticator_id"
            );

            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities.MFA_DISABLE
            );

            log.debug(
                    "Disable MFA request authorized: "
                            + "tenantId={}, subjectId={}, "
                            + "authenticatorId={}, correlationId={}",
                    secured.tenantId(),
                    secured.subjectId(),
                    request.getAuthenticatorId(),
                    request.getContext().getCorrelationId()
            );

            authenticatorService.disableAuthenticator(
                    secured.tenantId(),
                    secured.subjectId(),
                    request.getAuthenticatorId(),
                    secured.subjectId(),
                    request.getContext().getCorrelationId()
            );

            log.info(
                    "MFA authenticator disabled successfully: "
                            + "tenantId={}, subjectId={}, "
                            + "authenticatorId={}, correlationId={}",
                    secured.tenantId(),
                    secured.subjectId(),
                    request.getAuthenticatorId(),
                    request.getContext().getCorrelationId()
            );

            return DisableOwnMfaResponse.newBuilder()
                    .setDisabled(true)
                    .build();
        });
    }

    @Override
    public void regenerateRecoveryCodes(
            RegenerateRecoveryCodesRequest request,
            StreamObserver<RegenerateRecoveryCodesResponse>
                    responseObserver
    ) {
        log.info(
                "Received regenerate recovery codes request: "
                        + "subjectId={}, authenticatorId={}",
                request.getSubjectId(),
                request.getAuthenticatorId()
        );

        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            requireText(
                    request.getAuthenticatorId(),
                    "authenticator_id"
            );

            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities
                            .MFA_RECOVERY_REGENERATE
            );

            log.debug(
                    "Regenerate recovery codes request authorized: "
                            + "tenantId={}, subjectId={}, "
                            + "authenticatorId={}, correlationId={}",
                    secured.tenantId(),
                    secured.subjectId(),
                    request.getAuthenticatorId(),
                    request.getContext().getCorrelationId()
            );

            var codes =
                    authenticatorService.regenerateRecoveryCodes(
                            secured.tenantId(),
                            secured.subjectId(),
                            request.getAuthenticatorId(),
                            secured.subjectId(),
                            request.getContext().getCorrelationId()
                    );

            log.info(
                    "Recovery codes regenerated successfully: "
                            + "tenantId={}, subjectId={}, "
                            + "authenticatorId={}, codeCount={}, "
                            + "correlationId={}",
                    secured.tenantId(),
                    secured.subjectId(),
                    request.getAuthenticatorId(),
                    codes.size(),
                    request.getContext().getCorrelationId()
            );

            return RegenerateRecoveryCodesResponse.newBuilder()
                    .addAllRecoveryCodes(codes)
                    .build();
        });
    }

    @Override
    public void resetUserMfa(
            ResetUserMfaRequest request,
            StreamObserver<ResetUserMfaResponse>
                    responseObserver
    ) {
        log.info(
                "Received administrative MFA reset request: "
                        + "subjectId={}",
                request.getSubjectId()
        );

        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            requireText(request.getSubjectId(), "subject_id");

            requestSecurity.authorizeTenantAdministration(
                    request.getContext(),
                    IdentityGrpcAuthorities.USER_MFA_RESET
            );

            log.debug(
                    "Administrative MFA reset request authorized: "
                            + "tenantId={}, subjectId={}, actorId={}, "
                            + "correlationId={}",
                    request.getContext().getTenantId(),
                    request.getSubjectId(),
                    request.getContext().getActorId(),
                    request.getContext().getCorrelationId()
            );

            administrationService.resetUserMfa(
                    request.getContext().getTenantId(),
                    request.getSubjectId(),
                    request.getContext().getActorId(),
                    request.getContext().getCorrelationId()
            );

            log.info(
                    "User MFA reset successfully: "
                            + "tenantId={}, subjectId={}, actorId={}, "
                            + "correlationId={}",
                    request.getContext().getTenantId(),
                    request.getSubjectId(),
                    request.getContext().getActorId(),
                    request.getContext().getCorrelationId()
            );

            return ResetUserMfaResponse.newBuilder()
                    .setReset(true)
                    .build();
        });
    }

    @Override
    public void changeOwnPassword(
            ChangeOwnPasswordRequest request,
            StreamObserver<ChangeOwnPasswordResponse>
                    responseObserver
    ) {
        log.info(
                "Received change own password request: subjectId={}",
                request.getSubjectId()
        );

        exceptionMapper.executeUnary(responseObserver, () -> {
            requireContext(request.hasContext());
            requireText(
                    request.getCurrentPassword(),
                    "current_password"
            );
            requireText(
                    request.getNewPassword(),
                    "new_password"
            );

            var secured = requestSecurity.authorizeCredentialSelf(
                    request.getContext(),
                    request.getSubjectId(),
                    IdentityGrpcAuthorities.USER_CREDENTIAL_RESET
            );

            log.debug(
                    "Change own password request authorized: "
                            + "tenantId={}, subjectId={}, "
                            + "correlationId={}",
                    secured.tenantId(),
                    secured.subjectId(),
                    request.getContext().getCorrelationId()
            );

            accountService.changePassword(
                    secured.subjectId(),
                    request.getCurrentPassword(),
                    request.getNewPassword(),
                    secured.tenantId(),
                    secured.subjectId(),
                    request.getContext().getCorrelationId()
            );

            log.info(
                    "Password changed successfully: "
                            + "tenantId={}, subjectId={}, "
                            + "correlationId={}",
                    secured.tenantId(),
                    secured.subjectId(),
                    request.getContext().getCorrelationId()
            );

            return ChangeOwnPasswordResponse.newBuilder()
                    .setChanged(true)
                    .build();
        });
    }

    private SecurityProfileInfo toProto(
            SecurityProfile source
    ) {
        SecurityProfileInfo.Builder result =
                SecurityProfileInfo.newBuilder()
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
            log.warn(
                    "Identity credential request rejected: "
                            + "request context is missing"
            );

            throw new IllegalArgumentException(
                    "request context is required"
            );
        }
    }

    private void requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            log.warn(
                    "Identity credential request rejected: "
                            + "required field is blank, field={}",
                    field
            );

            throw new IllegalArgumentException(
                    field + " must not be blank"
            );
        }
    }
}