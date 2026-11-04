package com.htv.farmverse.identity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-0006: REST quản trị của identity chỉ nhận token có {@code aud} chứa {@code identity-service}. Token ký bằng
 * đúng khóa của identity, quyền SUPER_ADMIN, nhưng chỉ dành cho farm → 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AudienceTest {

    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;

    private String token(String... audiences) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost:8092").subject("user-1").audience(new ArrayList<>(List.of(audiences)))
                .issuedAt(now).expiresAt(now.plusSeconds(60))
                .claim("roles", new ArrayList<>(List.of("SUPER_ADMIN"))).claim("tenant_id", "t1")
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    @Test
    void identityAudienceIsAccepted() throws Exception {
        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("farm-service", "identity-service")))
                .andExpect(status().isOk());
    }

    @Test
    void tokenMeantOnlyForFarmIsRejected() throws Exception {
        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("farm-service")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SF-AUTH-001"));
    }

    @Test
    void legacySharedAudienceIsRejectedByDefault() throws Exception {
        mvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("farmverse-api")))
                .andExpect(status().isUnauthorized());
    }
}
