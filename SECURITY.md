# Security Policy

## Supported versions
Dự án đang ở giai đoạn tiền-release (V1 Core Platform). Chỉ nhánh mặc định (HEAD hiện tại) được hỗ trợ bảo mật. Chưa có bản release gắn version chính thức.

| Version | Supported |
|---------|:---------:|
| default branch (HEAD) | ✅ |
| older commits | ❌ |

## Reporting a vulnerability
Báo cáo lỗ hổng **riêng tư** cho chủ sở hữu repository (xem owner trong GitHub repo settings). **Không** mở public issue cho lỗ hổng chưa xử lý. Khi báo cáo, gồm: mô tả, bước tái hiện, phạm vi ảnh hưởng, phiên bản/commit. Không công khai chi tiết trước khi có bản vá.

## Secret management
- Không commit secret/token/private key. Dev dùng default trong `application-dev.yml` (postgres/root) — **chỉ dev**, không dùng ngoài local.
- Prod: inject qua biến môi trường (`.env.production.example` làm template): `SMARTFARM_JWT_ISSUER`, `SMARTFARM_JWKS_URI`, `GATEWAY_SERVICE_CLIENT_SECRET`, `IDENTITY_DB_*`, broker credential.

## Authentication
RS256 JWT (cap 15 phút) + JWKS; rotating refresh token với reuse-detection; TOTP MFA. Chi tiết: [`docs/security/security-architecture.md`](docs/security/security-architecture.md).

## Authorization
DB RBAC (role→permission + per-farm scope); gRPC fail-closed exact-method authz; REST scope qua `@PreAuthorize`.

## Transport security
- Nội bộ gRPC: TLS **chưa bật mặc định** (cần bật cho prod).
- MQTT: broker hiện `allow_anonymous true` — **phải** cấu hình auth + TLS + ACL cho prod (ISSUE-02).
- App-level MQTT message HMAC: đã implement, default-off; rollout theo thứ tự sign→verify.

## Dependency & container scanning
CI (`.github/workflows/build-push.yml`) sinh SBOM + provenance cho image GHCR. **Chưa có** scan-gate (fail-on-CVE) — đầu việc còn thiếu.

## Logging & audit
Correlation ID (`x-correlation-id`) xuyên gateway→service. gRPC audit configurable (default off ở gateway).

## Incident handling
Hiện chưa có runbook incident chuyên biệt ngoài `docs/operations/ORDER-OPERATIONS-RUNBOOK.md`. Đầu việc còn thiếu.

## Security checklist / đầu việc còn thiếu
Xem [`docs/security/security-architecture.md` §7](docs/security/security-architecture.md) và [`docs/audit/unresolved-items.md`](docs/audit/unresolved-items.md).
