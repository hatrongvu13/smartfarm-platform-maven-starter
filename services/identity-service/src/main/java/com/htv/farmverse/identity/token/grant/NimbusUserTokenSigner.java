package com.htv.farmverse.identity.token.grant;

import com.htv.farmverse.identity.token.TokenService;
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
        String value = tokens.encode(context);
        return new IssuedToken(
                value,
                "Bearer",
                context.audience(),
                clock.instant().plus(context.ttl())
        );
    }
}
