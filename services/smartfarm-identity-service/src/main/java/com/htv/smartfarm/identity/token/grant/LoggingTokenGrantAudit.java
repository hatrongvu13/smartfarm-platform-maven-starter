package com.htv.smartfarm.identity.token.grant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingTokenGrantAudit implements TokenGrantAudit {
    @Override
    public void granted(TokenContext context, IssuedToken token) {
        log.info(
                "Identity token granted: grantType={}, subjectId={}, tenantId={}, "
                        + "audience={}, sessionId={}",
                context.grantType(),
                context.subjectId(),
                context.tenantId(),
                context.audience(),
                context.sessionId()
        );
    }

    @Override
    public void denied(GrantType grantType, String reasonCode) {
        log.warn(
                "Identity token denied: grantType={}, reason={}",
                grantType,
                reasonCode
        );
    }
}
