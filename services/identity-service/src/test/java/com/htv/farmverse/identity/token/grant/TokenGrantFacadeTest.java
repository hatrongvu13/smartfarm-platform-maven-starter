package com.htv.farmverse.identity.token.grant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokenGrantFacadeTest {
    @Test
    void serviceGrantDoesNotCreateRefreshSession() {
        TokenSigner signer = mock(TokenSigner.class);
        RefreshSessionPort sessions = mock(RefreshSessionPort.class);
        TokenGrantAudit audit = mock(TokenGrantAudit.class);
        TokenContext context = context(GrantType.SERVICE_CLIENT, null, Set.of("inventory:read"));
        IssuedToken token = new IssuedToken("jwt", "Bearer", "inventory", Instant.now().plusSeconds(60));
        when(signer.sign(context)).thenReturn(token);

        TokenGrant grant = new TokenGrantFacade(signer, sessions, audit).issue(context);

        assertThat(grant.refreshToken()).isNull();
        verifyNoInteractions(sessions);
        verify(audit).granted(context, token);
    }

    @Test
    void userGrantCreatesRefreshSession() {
        TokenSigner signer = mock(TokenSigner.class);
        RefreshSessionPort sessions = mock(RefreshSessionPort.class);
        TokenGrantAudit audit = mock(TokenGrantAudit.class);
        TokenContext context = context(GrantType.USER_LOGIN, null, Set.of("farm:read"));
        IssuedToken token = new IssuedToken("jwt", "Bearer", "inventory", Instant.now().plusSeconds(60));
        when(signer.sign(context)).thenReturn(token);
        when(sessions.create(context)).thenReturn(new RefreshSessionPort.RefreshGrant("refresh", "sid", "family"));

        TokenGrant grant = new TokenGrantFacade(signer, sessions, audit).issue(context);

        assertThat(grant.refreshToken()).isEqualTo("refresh");
        assertThat(grant.sessionId()).isEqualTo("sid");
    }

    @Test
    void delegatedGrantRequiresActor() {
        assertThatThrownBy(() -> context(GrantType.DELEGATED_SERVICE, null, Set.of("farm:read")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorSubjectId");
    }

    @Test
    void serviceGrantRejectsWildcard() {
        assertThatThrownBy(() -> context(GrantType.SERVICE_CLIENT, null, Set.of("*")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcard");
    }

    private static TokenContext context(GrantType type, String actor, Set<String> scopes) {
        boolean service = type == GrantType.SERVICE_CLIENT || type == GrantType.DELEGATED_SERVICE;
        return new TokenContext(type, service ? "svc:gateway" : "user-1", "tenant-1", "inventory",
                service ? "gateway" : null, actor, scopes, Set.of("USER"), 1, 2, null,
                Instant.now(), List.of("pwd"), Duration.ofMinutes(5));
    }
}
