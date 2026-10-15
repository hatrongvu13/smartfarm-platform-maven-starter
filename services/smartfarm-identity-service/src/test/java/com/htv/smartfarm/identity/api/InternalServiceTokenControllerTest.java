package com.htv.smartfarm.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.htv.smartfarm.identity.token.ServiceTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalServiceTokenControllerTest {
    private static final String INTERNAL_SECRET =
            "test-identity-internal-api-secret";

    private final ServiceTokenService serviceTokens =
            mock(ServiceTokenService.class);
    private final InternalServiceTokenController controller =
            new InternalServiceTokenController(serviceTokens, INTERNAL_SECRET);

    @Test
    void rejectsMissingInternalSecretBeforeIssuingToken() {
        assertThatThrownBy(() -> controller.issue(request(), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");

        verifyNoInteractions(serviceTokens);
    }

    @Test
    void rejectsInvalidInternalSecretBeforeIssuingToken() {
        assertThatThrownBy(() -> controller.issue(request(), "wrong-secret"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");

        verifyNoInteractions(serviceTokens);
    }

    @Test
    void failsClosedWhenInternalSecretIsNotConfigured() {
        var unconfigured = new InternalServiceTokenController(serviceTokens, "");

        assertThatThrownBy(() -> unconfigured.issue(request(), INTERNAL_SECRET))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");

        verifyNoInteractions(serviceTokens);
    }

    @Test
    void passesRequestToTokenServiceAfterInternalSecretIsValidated() {
        var expected = new ServiceTokenService.Grant(
                "service-jwt",
                "Bearer",
                300,
                "smartfarm-identity"
        );
        when(serviceTokens.issue(
                "gateway",
                "client-secret",
                "smartfarm-identity",
                "tenant-1",
                null
        )).thenReturn(expected);

        var actual = controller.issue(request(), INTERNAL_SECRET);

        assertThat(actual).isSameAs(expected);
        verify(serviceTokens).issue(
                "gateway",
                "client-secret",
                "smartfarm-identity",
                "tenant-1",
                null
        );
    }

    private static InternalServiceTokenController.TokenRequest request() {
        return new InternalServiceTokenController.TokenRequest(
                "gateway",
                "client-secret",
                "smartfarm-identity",
                "tenant-1",
                null
        );
    }
}
