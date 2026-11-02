package com.htv.farmverse.identity.audit;

import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityAuditTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("SECURITY_AUDIT");
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attach() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        SecurityContextHolder.clearContext();
    }

    @Test
    void writesOneLineWithActorFromSecurityContextAndStructuredKeyValues() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin-1", "n/a"));

        SecurityAudit.warn("user.roles.replaced", "tenant", "t1", "from", List.of("A"), "to", List.of("A", "B"));

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage())
                .isEqualTo("event=user.roles.replaced actor=admin-1 tenant=t1 from=[A] to=[A,B]");
        assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key)
                .containsExactly("event", "actor", "tenant", "from", "to");
    }

    @Test
    void noAuthenticationMeansDashActor_andExplicitActorWins() {
        SecurityAudit.info("a.b");
        SecurityAudit.info("c.d", "actor", "system");

        assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("event=a.b actor=-");
        assertThat(appender.list.get(1).getFormattedMessage()).isEqualTo("event=c.d actor=system");
    }

    @Test
    void valuesCannotInjectFakeLogLinesOrFields() {
        SecurityAudit.info("authentication.failure", "actor", "bob\nevent=admin.granted actor=root");

        String message = appender.list.get(0).getFormattedMessage();
        assertThat(message).doesNotContain("\n").doesNotContain(" actor=root");
        assertThat(message).isEqualTo("event=authentication.failure actor=bob_event=admin.granted_actor=root");
    }

    @Test
    void fieldsMustComeInPairs() {
        assertThatThrownBy(() -> SecurityAudit.info("x", "only-key")).isInstanceOf(IllegalArgumentException.class);
    }
}
