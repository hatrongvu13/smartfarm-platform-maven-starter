package com.htv.smartfarm.identity.token.grant;

import com.htv.smartfarm.identity.token.TokenService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Bridges the unified grant facade to the existing RS256 signer. */
@Component
@RequiredArgsConstructor
public class NimbusUserTokenSigner implements TokenSigner {
    private final TokenService tokens;
    private final Clock clock;

    @Override
    public IssuedToken sign(TokenContext context) {
        if (context.grantType() == GrantType.SERVICE_CLIENT
                || context.grantType() == GrantType.DELEGATED_SERVICE) {
            throw new IllegalArgumentException(
                    "Service grants must use the service-token policy path"
            );
        }
        String value = tokens.issueUserToken(
                context.subjectId(),
                context.tenantId(),
                context.scopes(),
                context.roles(),
                context.ttl()
        );
        return new IssuedToken(
                value,
                "Bearer",
                context.audience(),
                clock.instant().plus(context.ttl())
        );
    }
}
