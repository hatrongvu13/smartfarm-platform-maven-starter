package com.htv.farmverse.identity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Identity với mTLS "want": trình duyệt (không cert) vẫn vào được trang login,
 * còn đường máy-gọi-máy (JWKS, token) đòi cert của gateway / farm.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "farmverse.identity.issuer=https://localhost:8092"})
@ActiveProfiles({"test", "mtls"})
class MtlsIntegrationTest {

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

    @LocalServerPort
    int port;

    @Test
    void browserWithoutCertificateCanOpenLoginPage() throws Exception {
        assertThat(get(browser(), "/login").statusCode()).isEqualTo(200);
    }

    @Test
    void backChannelRequiresServiceCertificate() throws Exception {
        HttpResponse<String> anonymous = get(browser(), "/oauth2/jwks");
        assertThat(anonymous.statusCode()).isEqualTo(403);
        assertThat(anonymous.body()).contains("SF-MTLS-001");

        assertThat(get(service("farm"), "/oauth2/jwks").statusCode()).isEqualTo(200);
        assertThat(get(service("gateway"), "/oauth2/jwks").statusCode()).isEqualTo(200);
        assertThat(get(service("identity"), "/oauth2/jwks").statusCode()).isEqualTo(403); // không trong danh sách
    }

    private HttpResponse<String> get(SSLContext ssl, String path) throws Exception {
        return HttpClient.newBuilder().sslContext(ssl).build().send(
                HttpRequest.newBuilder(URI.create("https://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static SSLContext browser() {
        return SslBundle.of(new PemSslStoreBundle(null,
                PemSslStoreDetails.forCertificate("file:" + certs.resolve("ca.crt")))).createSslContext();
    }

    private static SSLContext service(String name) {
        return SslBundle.of(new PemSslStoreBundle(
                PemSslStoreDetails.forCertificate("file:" + certs.resolve(name + ".crt"))
                        .withPrivateKey("file:" + certs.resolve(name + ".key")),
                PemSslStoreDetails.forCertificate("file:" + certs.resolve("ca.crt")))).createSslContext();
    }
}
