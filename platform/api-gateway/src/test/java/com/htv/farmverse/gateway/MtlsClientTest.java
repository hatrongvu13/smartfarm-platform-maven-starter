package com.htv.farmverse.gateway;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phía client của mTLS: WebClient dùng cho identity trình chứng chỉ gateway
 * cho một server HTTPS bắt buộc cert client (giống identity / farm thật).
 */
@SpringBootTest(properties = {
        "farmverse.gateway.oidc.client-secret=test-secret",
        "spring.data.redis.password=test"
})
@ActiveProfiles({"test", "mtls"})
class MtlsClientTest {

    static Path certs;

    @DynamicPropertySource
    static void certificates(DynamicPropertyRegistry registry) throws Exception {
        if (certs == null) {
            certs = Files.createTempDirectory("farmverse-mtls");
            Path script = Path.of("../../scripts/generate-dev-mtls.sh").toAbsolutePath().normalize();
            Process process = new ProcessBuilder("bash", script.toString(), certs.toString(), "--force")
                    .redirectErrorStream(true).start();
            assertThat(process.waitFor()).as(new String(process.getInputStream().readAllBytes())).isZero();
        }
        registry.add("farmverse.mtls.dir", () -> certs.toString());
    }

    @Autowired
    WebClient identityWebClient;

    @Test
    void identityClientPresentsGatewayCertificate() throws Exception {
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext()) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters ssl = getSSLContext().getDefaultSSLParameters();
                ssl.setNeedClientAuth(true);
                params.setSSLParameters(ssl);
            }
        });
        server.createContext("/whoami", exchange -> {
            SSLSession session = ((HttpsExchange) exchange).getSSLSession();
            X509Certificate peer = (X509Certificate) session.getPeerCertificates()[0];
            byte[] body;
            try {
                body = peer.getSubjectAlternativeNames().toString().getBytes(StandardCharsets.UTF_8);
            } catch (java.security.cert.CertificateParsingException e) {
                throw new java.io.IOException(e);
            }
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String seen = identityWebClient.get()
                    .uri("https://localhost:" + server.getAddress().getPort() + "/whoami")
                    .retrieve().bodyToMono(String.class).block();
            assertThat(seen).contains("spiffe://farmverse/gateway");
        } finally {
            server.stop(0);
        }
    }

    private static SSLContext serverContext() {
        return SslBundle.of(new PemSslStoreBundle(
                PemSslStoreDetails.forCertificate("file:" + certs.resolve("identity.crt"))
                        .withPrivateKey("file:" + certs.resolve("identity.key")),
                PemSslStoreDetails.forCertificate("file:" + certs.resolve("ca.crt")))).createSslContext();
    }
}
