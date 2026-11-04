package com.htv.farmverse.farm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M7 ở farm: nhận {@code traceparent} từ gateway và ghi trace id vào log; token không lọt vào log kể cả ở DEBUG;
 * metric Prometheus không mở ở cổng ứng dụng (chỉ ở cổng quản trị của profile mtls).
 */
@SpringBootTest(properties = {
        "logging.level.org.springframework.security=DEBUG",
        "logging.level.org.springframework.web=DEBUG"
})
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class FarmObservabilityTest {

    static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

    @Autowired MockMvc mvc;

    @Test
    void traceFromGatewayIsInTheLogsAndTokenIsNot(CapturedOutput output) throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.farm-secret-token-7d3a.c2ln";

        mvc.perform(get("/farms")
                        .header("traceparent", "00-" + TRACE_ID + "-b7ad6b7169203331-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        assertThat(output.getAll())
                .contains(" DEBUG ")                 // DEBUG đang bật thật
                .contains(TRACE_ID)                  // log nối được với gateway bằng trace id
                .doesNotContain("farm-secret-token");
    }

    @Test
    void prometheusIsNotOnTheApplicationPort() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(result ->
                assertThat(result.getResponse().getContentAsString()).doesNotContain("jvm_"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());   // actuator vẫn chạy, chỉ không mở prometheus
    }
}
