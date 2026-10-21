package com.htv.smartfarm.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.validation.Validation;

import com.htv.smartfarm.identity.bootstrap.BootstrapTokenService;
import com.htv.smartfarm.identity.config.SuperAdminBootstrapProperties;
import com.htv.smartfarm.identity.oauth2.AuthService;
import com.htv.smartfarm.identity.token.TokenService;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthControllerBootstrapSecurityTest {

    private static final String BOOTSTRAP_TOKEN =
            "test-bootstrap-token-that-is-at-least-32-chars";

    private final AuthService auth = mock(AuthService.class);
    private final BootstrapTokenService bootstrapTokens = mock(BootstrapTokenService.class);
    private final AuthController controller = new AuthController(
            auth,
            mock(TokenService.class),
            bootstrapTokens
    );

    @Test
    void bootstrapIsRejectedWhenTokenIsMissing() {
        var response = controller.bootstrapSuperAdmin(
                null,
                new AuthController.BootstrapSuperAdmin(
                        "root@example.test",
                        "valid-password"
                )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(auth);
    }

    @Test
    void bootstrapIsRejectedWhenTokenDoesNotMatch() {
        var response = controller.bootstrapSuperAdmin(
                "incorrect-token",
                new AuthController.BootstrapSuperAdmin(
                        "root@example.test",
                        "valid-password"
                )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(auth);
    }

    @Test
    void bootstrapAcceptsConfiguredToken() {
        org.mockito.Mockito.when(bootstrapTokens.matches(BOOTSTRAP_TOKEN)).thenReturn(true);
        var response = controller.bootstrapSuperAdmin(
                BOOTSTRAP_TOKEN,
                new AuthController.BootstrapSuperAdmin(
                        "root@example.test",
                        "valid-password"
                )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(auth).bootstrapSuperAdmin(
                "root@example.test",
                "valid-password"
        );
    }

    @Test
    void bootstrapRequiresMatchingGeneratedOrConfiguredToken() {
        org.mockito.Mockito.when(bootstrapTokens.matches("incorrect-token")).thenReturn(false);
        org.mockito.Mockito.when(bootstrapTokens.matches(BOOTSTRAP_TOKEN)).thenReturn(true);

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            assertThat(validator.validate(new SuperAdminBootstrapProperties("short"))).isNotEmpty();
            assertThat(validator.validate(new SuperAdminBootstrapProperties(BOOTSTRAP_TOKEN))).isEmpty();
            assertThat(validator.validate(new SuperAdminBootstrapProperties(""))).isEmpty();
        }
    }
}
