package com.htv.farmverse.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tài liệu OpenAPI của REST quản trị: có đường dẫn chính, khai Bearer, và schema lỗi Problem. */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void publishesAdminApiWithBearerAndProblemSchema() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FarmVerse Identity API"))
                .andExpect(jsonPath("$.paths['/users']").exists())
                .andExpect(jsonPath("$.paths['/me/mfa/totp']").exists())
                .andExpect(jsonPath("$.paths['/roles']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.Problem.properties.code").exists())
                .andExpect(jsonPath("$.paths['/users'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/users'].get.responses['403']").exists());
    }
}
