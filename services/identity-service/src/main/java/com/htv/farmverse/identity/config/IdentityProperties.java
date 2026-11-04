package com.htv.farmverse.identity.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/**
 * Toàn bộ cấu hình riêng của identity, prefix {@code farmverse.identity}.
 *
 * @param issuer     URL issuer công bố trong discovery và claim {@code iss}
 * @param signingKey cặp khóa RSA ký token (PEM)
 * @param lockout    khóa tạm tài khoản khi nhập sai mật khẩu nhiều lần
 * @param clients    client OAuth2 được nạp vào bảng {@code oauth2_registered_client} lúc khởi động
 * @param catalog    danh mục permission của nền tảng (seed, đánh dấu system_owned)
 * @param bootstrap  super admin đầu tiên; để trống email thì bỏ qua
 * @param mfa        TOTP: tên hiển thị trong app authenticator và khóa mã hóa secret
 * @param rateLimit  giới hạn lần thử đăng nhập theo IP; bỏ trống = 10 lần mỗi phút
 */
@Validated
@ConfigurationProperties("farmverse.identity")
public record IdentityProperties(
        @NotBlank String issuer,
        @Valid @NotNull SigningKey signingKey,
        @Valid @NotNull Lockout lockout,
        @Valid List<Client> clients,
        @Valid Catalog catalog,
        @Valid Bootstrap bootstrap,
        @Valid @NotNull Mfa mfa,
        @Valid RateLimit rateLimit
) {

    public IdentityProperties {
        rateLimit = rateLimit == null ? new RateLimit(10, Duration.ofMinutes(1), RateLimit.Store.JDBC) : rateLimit;
        clients = clients == null ? List.of() : List.copyOf(clients);
        catalog = catalog == null ? new Catalog(List.of()) : catalog;
        bootstrap = bootstrap == null ? new Bootstrap(null, null, null) : bootstrap;
    }

    /**
     * Khóa ký đang dùng, cộng các khóa đã nghỉ để xoay khóa không làm rớt token còn hạn.
     *
     * @param keyId     định danh khóa đang ký, ghi vào header {@code kid} của token
     * @param ephemeral chỉ cho test: sinh cặp khóa mới trong bộ nhớ mỗi lần chạy, bỏ qua file PEM
     * @param retired   khóa cũ: chỉ còn công bố khóa công khai ở JWKS để kiểm chữ ký, không bao giờ ký nữa.
     *                  Gỡ khỏi danh sách sau khi token cuối cùng ký bằng nó đã hết hạn.
     */
    public record SigningKey(@NotBlank String keyId, Resource privateKey, Resource publicKey, boolean ephemeral,
                             @Valid List<RetiredKey> retired) {

        public SigningKey {
            retired = retired == null ? List.of() : List.copyOf(retired);
        }
    }

    /** Khóa đã nghỉ: chỉ cần khóa công khai. */
    public record RetiredKey(@NotBlank String keyId, @NotNull Resource publicKey) {
    }

    public record Lockout(@Positive int maxFailures, @NotNull Duration duration) {
    }

    /**
     * Giới hạn lần thử đăng nhập theo IP ({@code POST /login}, {@code POST /login/totp}).
     *
     * @param maxAttempts số lần tối đa trong một cửa sổ
     * @param window      độ dài cửa sổ
     * @param store       nơi đếm: {@code jdbc} (PostgreSQL, chung mọi instance, mặc định) hoặc {@code memory}
     *                    (một instance, chỉ cho test trên H2) — M8
     */
    public record RateLimit(@DefaultValue("10") @Positive int maxAttempts,
                            @DefaultValue("1m") @NotNull Duration window,
                            @DefaultValue("jdbc") @NotNull Store store) {

        public enum Store { JDBC, MEMORY }
    }

    /**
     * @param clientSecret giá trị thô (dev) — được băm BCrypt trước khi lưu
     */
    public record Client(
            @NotBlank String clientId,
            @NotBlank @Size(min = 32) String clientSecret,
            @NotBlank String name,
            @NotEmpty Set<String> grantTypes,
            Set<String> redirectUris,
            Set<String> postLogoutRedirectUris,
            @NotEmpty Set<String> scopes,
            @DefaultValue("5m") Duration accessTokenTtl,
            @DefaultValue("14d") Duration refreshTokenTtl
    ) {
        public Client {
            redirectUris = redirectUris == null ? Set.of() : Set.copyOf(redirectUris);
            postLogoutRedirectUris = postLogoutRedirectUris == null ? Set.of() : Set.copyOf(postLogoutRedirectUris);
        }

        @Override
        public String toString() {
            return "Client[clientId=" + clientId + ", grantTypes=" + grantTypes + ", secret=[REDACTED]]";
        }
    }

    public record Catalog(@Valid List<PermissionSeed> permissions) {
        public Catalog {
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
        }
    }

    public record PermissionSeed(@NotBlank String code, @NotBlank String resourceType,
                                 @NotBlank String action, String description) {
    }

    /**
     * @param encryptionPassword mật khẩu dẫn ra khóa AES (PBKDF2) — bí mật, prod lấy từ biến môi trường
     * @param encryptionSalt     salt dạng hex (ít nhất 16 ký tự hex)
     */
    public record Mfa(@NotBlank String issuer,
                      @NotBlank @Size(min = 16) String encryptionPassword,
                      @NotBlank @jakarta.validation.constraints.Pattern(regexp = "([0-9a-fA-F]{2}){8,}") String encryptionSalt) {

        @Override
        public String toString() {
            return "Mfa[issuer=" + issuer + ", encryption=[REDACTED]]";
        }
    }

    public record Bootstrap(String adminEmail, String adminPassword, String adminDisplayName) {

        public boolean enabled() {
            return adminEmail != null && !adminEmail.isBlank();
        }

        @Override
        public String toString() {
            return "Bootstrap[adminEmail=" + adminEmail + ", adminPassword=[REDACTED]]";
        }
    }
}
