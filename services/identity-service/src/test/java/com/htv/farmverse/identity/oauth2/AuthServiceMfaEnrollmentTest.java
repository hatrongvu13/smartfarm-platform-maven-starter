package com.htv.farmverse.identity.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import com.htv.farmverse.identity.account.application.AccountService;
import com.htv.farmverse.identity.account.repository.UserAccountRepository;
import com.htv.farmverse.identity.authorization.application.RoleManagementService;
import com.htv.farmverse.identity.authorization.repository.MembershipRoleRepository;
import com.htv.farmverse.identity.authorization.repository.RolePermissionRepository;
import com.htv.farmverse.identity.authorization.repository.RoleRepository;
import com.htv.farmverse.identity.authorization.version.AuthorizationVersionService;
import com.htv.farmverse.identity.config.IdentitySettings;
import com.htv.farmverse.identity.mfa.application.AuthenticatorService;
import com.htv.farmverse.identity.mfa.application.MfaChallengeService;
import com.htv.farmverse.identity.mfa.application.MfaRequirementService;
import com.htv.farmverse.identity.mfa.application.model.TotpEnrollment;
import com.htv.farmverse.identity.mfa.domain.MfaChallengeEntity;
import com.htv.farmverse.identity.mfa.domain.MfaChallengePurpose;
import com.htv.farmverse.identity.tenant.repository.TenantMembershipRepository;
import com.htv.farmverse.identity.tenant.repository.TenantRepository;
import com.htv.farmverse.identity.token.grant.RefreshSessionPort;
import com.htv.farmverse.identity.token.grant.TokenGrantFacade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceMfaEnrollmentTest {

    @Mock private UserAccountRepository accounts;
    @Mock private TenantRepository tenants;
    @Mock private TenantMembershipRepository memberships;
    @Mock private MembershipRoleRepository membershipRoles;
    @Mock private RoleRepository roles;
    @Mock private RolePermissionRepository rolePermissions;
    @Mock private AccountService accountService;
    @Mock private RefreshSessionPort refreshSessions;
    @Mock private TokenGrantFacade tokenGrants;
    @Mock private AuthorizationVersionService authorizationVersions;
    @Mock private PasswordEncoder encoder;
    @Mock private IdentitySettings settings;
    @Mock private MfaRequirementService mfaRequirements;
    @Mock private AuthenticatorService authenticators;
    @Mock private MfaChallengeService challenges;
    @Mock private java.time.Clock clock;
    @Mock private RoleManagementService roleManagement;

    @InjectMocks
    private AuthService auth;

    @Test
    void beginsEnrollmentOnlyForPendingEnrollmentChallenge() {
        var challenge = new MfaChallengeEntity(
                "challenge-id",
                "user-id",
                "tenant-id",
                MfaChallengePurpose.TOTP_ENROLLMENT,
                "challenge-hash",
                Instant.now().plusSeconds(60)
        );
        var enrollment = new TotpEnrollment(
                "authenticator-id",
                "otpauth://totp/example",
                Instant.now().plusSeconds(60)
        );
        when(challenges.requirePending(
                "challenge-token",
                MfaChallengePurpose.TOTP_ENROLLMENT
        )).thenReturn(challenge);
        when(authenticators.beginTotpEnrollment("user-id", "Admin device"))
                .thenReturn(enrollment);

        var result = auth.beginRequiredTotpEnrollment(
                "challenge-token",
                "Admin device"
        );

        assertThat(result).isEqualTo(enrollment);
        verify(authenticators).beginTotpEnrollment("user-id", "Admin device");
    }

    @Test
    void rejectsInvalidEnrollmentChallengeBeforeCreatingAuthenticator() {
        when(challenges.requirePending(
                "invalid-token",
                MfaChallengePurpose.TOTP_ENROLLMENT
        )).thenThrow(new IllegalArgumentException("Challenge is invalid"));

        assertThatThrownBy(() -> auth.beginRequiredTotpEnrollment(
                "invalid-token",
                "Admin device"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Challenge is invalid");

        verify(authenticators, never())
                .beginTotpEnrollment("user-id", "Admin device");
    }
}
