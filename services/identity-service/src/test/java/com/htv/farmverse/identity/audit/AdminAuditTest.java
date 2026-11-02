package com.htv.farmverse.identity.audit;

import java.util.List;

import com.htv.farmverse.identity.tenant.Tenant;
import com.htv.farmverse.identity.tenant.TenantRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Đổi role qua REST quản trị phải để lại dòng SECURITY_AUDIT có người làm, tenant và danh sách quyền. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TenantRepository tenants;

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
    }

    @Test
    void creatingAndDeletingARoleIsAudited() throws Exception {
        String tenantId = tenants.findByCode(Tenant.PLATFORM_CODE).orElseThrow().getId();
        RequestPostProcessor admin = jwt()
                .jwt(token -> token.subject("admin-1").claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("identity:role:manage"));

        mvc.perform(post("/roles").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AUDIT_TEST\",\"name\":\"Audit test\",\"permissions\":[\"farm:farm:read\"]}"))
                .andExpect(status().isCreated());
        mvc.perform(delete("/roles/AUDIT_TEST").with(admin)).andExpect(status().isNoContent());

        List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines).contains(
                "event=role.created actor=admin-1 tenant=" + tenantId + " role=AUDIT_TEST permissions=[farm:farm:read]",
                "event=role.deleted actor=admin-1 tenant=" + tenantId + " role=AUDIT_TEST permissions=[farm:farm:read]");
    }
}
