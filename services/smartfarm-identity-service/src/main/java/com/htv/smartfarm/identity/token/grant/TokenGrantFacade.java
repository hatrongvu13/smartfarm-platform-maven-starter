package com.htv.smartfarm.identity.token.grant;

import java.util.Objects;
import org.springframework.stereotype.Service;

/** The only business component allowed to issue platform tokens. */
@Service
public final class TokenGrantFacade {
    private final TokenSigner signer;
    private final RefreshSessionPort refreshSessions;
    private final TokenGrantAudit audit;

    public TokenGrantFacade(
            TokenSigner signer,
            RefreshSessionPort refreshSessions,
            TokenGrantAudit audit
    ) {
        this.signer = Objects.requireNonNull(signer);
        this.refreshSessions = Objects.requireNonNull(refreshSessions);
        this.audit = Objects.requireNonNull(audit);
    }

    public TokenGrant issue(TokenContext context) {
        Objects.requireNonNull(context, "context");
        IssuedToken accessToken = signer.sign(context);
        if (serviceGrant(context.grantType())) {
            audit.granted(context, accessToken);
            return TokenGrant.accessOnly(accessToken);
        }
        RefreshSessionPort.RefreshGrant refresh = refreshSessions.create(context);
        audit.granted(context, accessToken);
        return new TokenGrant(accessToken, refresh.rawToken(), refresh.sessionId());
    }

    public TokenGrant rotate(String rawRefreshToken, TokenContext renewedContext) {
        Objects.requireNonNull(renewedContext, "renewedContext");
        if (renewedContext.grantType() != GrantType.REFRESH_ROTATION) {
            throw new IllegalArgumentException("renewedContext must use REFRESH_ROTATION");
        }
        RefreshSessionPort.RefreshGrant refresh = refreshSessions.rotate(rawRefreshToken);
        IssuedToken accessToken = signer.sign(renewedContext);
        audit.granted(renewedContext, accessToken);
        return new TokenGrant(accessToken, refresh.rawToken(), refresh.sessionId());
    }

    private static boolean serviceGrant(GrantType type) {
        return type == GrantType.SERVICE_CLIENT || type == GrantType.DELEGATED_SERVICE;
    }
}
