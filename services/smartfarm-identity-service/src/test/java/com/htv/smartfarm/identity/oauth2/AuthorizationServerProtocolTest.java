package com.htv.smartfarm.identity.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import com.htv.smartfarm.identity.token.TokenService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.grpc.server.port=0",
                "smartfarm.identity.mqtt.enabled=false",
                "smartfarm.identity.identity-audience=smartfarm-identity",
                "smartfarm.identity.issuer=http://localhost:8092",
                "smartfarm.identity.private-key-path=classpath:.local/identity-private.pem",
                "smartfarm.identity.public-key-path=classpath:.local/identity-public.pem",
                "smartfarm.security.jwt.issuer=http://localhost:8092",
                "smartfarm.security.jwt.jwk-set-uri=http://localhost:8092/.well-known/jwks.json",
                "smartfarm.security.jwt.audiences=smartfarm-identity",
                "smartfarm.security.jwt.allow-local-http=true"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationServerProtocolTest {

    private static final String CLIENT_ID = "identity-protocol-test";
    private static final String CLIENT_SECRET =
            "protocol-test-secret-that-is-long-enough-for-policy-32";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokens;

    private JdbcRegisteredClientRepository clients;

    @BeforeEach
    void registerConfidentialMachineClient() {
        clients = new JdbcRegisteredClientRepository(jdbc);
        RegisteredClient client = clients.findByClientId(CLIENT_ID);
        if (client == null) {
            clients.save(RegisteredClient.withId("oauth-protocol-test-id")
                    .clientId(CLIENT_ID)
                    .clientSecret(passwordEncoder.encode(CLIENT_SECRET))
                    .clientName("Protocol test client")
                    .clientAuthenticationMethod(
                            ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                    )
                    .authorizationGrantType(
                            AuthorizationGrantType.CLIENT_CREDENTIALS
                    )
                    .scope("orders:read")
                    .clientSettings(ClientSettings.builder()
                            .setting(
                                    "identity.audiences",
                                    "smartfarm-identity"
                            )
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofMinutes(5))
                            .build())
                    .build());
        }
    }

    @Test
    void publishesOidcDiscoveryAndJwksMetadata() throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer")
                        .value("http://localhost:8092"))
                .andExpect(jsonPath("$.authorization_endpoint")
                        .value("http://localhost:8092/oauth2/authorize"))
                .andExpect(jsonPath("$.token_endpoint")
                        .value("http://localhost:8092/oauth2/token"))
                .andExpect(jsonPath("$.jwks_uri")
                        .value("http://localhost:8092/oauth2/jwks"));
    }

    @Test
    void issuesShortLivedAudienceBoundMachineToken() throws Exception {
        String response = mvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .param("grant_type", "client_credentials")
                        .param("scope", "orders:read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber())
                .andExpect(jsonPath("$.scope").value("orders:read"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String rawToken = com.fasterxml.jackson.databind.json.JsonMapper
                .builder()
                .build()
                .readTree(response)
                .get("access_token")
                .asText();
        var jwt = tokens.decoder().decode(rawToken);
        assertThat(jwt.getAudience()).containsExactly("smartfarm-identity");
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(jwt.getClaimAsString("token_type")).isEqualTo("service");
        assertThat(jwt.getClaimAsString("tenant_id")).isNull();
    }
}
