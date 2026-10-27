package com.htv.farmverse.farm;

import java.io.IOException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * mTLS thật trên cổng HTTPS: chứng chỉ do chính {@code scripts/generate-dev-mtls.sh} tạo.
 * Lớp 1 (TLS): không cert → bắt tay thất bại. Lớp 2 (ServiceIdentityFilter): cert hợp lệ nhưng không phải gateway → 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
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
    void withoutClientCertificateTheHandshakeFails() {
        HttpClient client = HttpClient.newBuilder().sslContext(trustOnly()).build();
        assertThatThrownBy(() -> client.send(request(), HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void certificateOfAnotherServiceIsRejected() throws Exception {
        HttpResponse<String> response = HttpClient.newBuilder().sslContext(identityOf("identity")).build()
                .send(request(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("SF-MTLS-001");
    }

    @Test
    void gatewayCertificatePassesTransportThenJwtIsRequired() throws Exception {
        HttpResponse<String> response = HttpClient.newBuilder().sslContext(identityOf("gateway")).build()
                .send(request(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401); // qua mTLS, tới Spring Security: thiếu Bearer
    }

    private HttpRequest request() {
        return HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/farms")).GET().build();
    }

    private static SSLContext identityOf(String service) {
        PemSslStoreDetails keyStore = PemSslStoreDetails.forCertificate("file:" + certs.resolve(service + ".crt"))
                .withPrivateKey("file:" + certs.resolve(service + ".key"));
        PemSslStoreDetails trustStore = PemSslStoreDetails.forCertificate("file:" + certs.resolve("ca.crt"));
        return SslBundle.of(new PemSslStoreBundle(keyStore, trustStore)).createSslContext();
    }

    private static SSLContext trustOnly() {
        PemSslStoreDetails trustStore = PemSslStoreDetails.forCertificate("file:" + certs.resolve("ca.crt"));
        return SslBundle.of(new PemSslStoreBundle(null, trustStore)).createSslContext();
    }
}
