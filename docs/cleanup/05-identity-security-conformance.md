# 05 — Identity / TOTP / Admin Security Conformance (Phase 4)

> **AUDIT-ONLY — no identity source changed.** Reviewed against the Phase 4 checklist.
> Guiding rule (source-of-truth priority #2): **never downgrade security to match an
> older/weaker backlog.** Where the implementation is stronger than backlog, that is
> KEEP + DOCUMENT, not a change. Gaps with real impact become scoped **UPDATE** items
> (no identity rewrite).
> Labels: **FACT / INFERENCE / UNKNOWN / RISK / KEEP / UPDATE**.

Evidence base (FACT): read `mfa/application/TotpService.java`,
`mfa/domain/UserAuthenticatorEntity.java`, `mfa/domain/MfaChallengeEntity.java`,
`mfa/config/MfaProperties.java`, `mfa/crypto/*`, `oauth2/AuthService.java`,
`account/domain/UserAccountEntity.java`, `bootstrap/DevSuperAdminBootstrap.java`,
`libs/smartfarm-security/.../SmartFarmJwtValidator.java`, `JwtTokenVerifier.java`,
plus the HMAC MQTT verifier (`MqttSecurityVerifier`, ISSUE-01/02).

## 1. Checklist conformance

| # | Checklist item | Status | Evidence (FACT unless noted) |
|---|---|---|---|
| 1 | Single `SUPER_ADMIN` bootstrapped | **PARTIAL / RISK** | Bootstrap exists but is `@Profile("dev & !prod")` (`DevSuperAdminBootstrap`). **No prod bootstrap path in source** — see RISK-1. Single-super-admin *uniqueness* is not enforced by an explicit guard I could find (RISK-2). |
| 2 | Super-admin first-login TOTP enrollment before privileged ops | **MATCHED (INFERENCE)** | `MfaRequirementService` + `MfaChallengeService` gate; enrollment flow (`beginTotpEnrollment`/`confirmTotpEnrollment`) present. Confirm the *enforcement point* blocks privileged ops pre-enrollment (INFERENCE from wiring). |
| 3 | Enrollment secret not logged / not returned after confirm | **MATCHED** | Secret stored via `AesGcmSecretCipher` (`mfa/crypto`); no log of raw secret found; enrollment model returns provisioning data only during begin, not after confirm. |
| 4 | TOTP verify: rate limit, replay prevention, time-step policy, audit on failure | **MATCHED (strong)** | Replay: `UserAuthenticatorEntity.acceptTotpStep` keeps `lastAcceptedTimeStep` and **rejects `timeStep <= lastAcceptedTimeStep`** (monotonic — a used step cannot be replayed). Rate limit: `MfaChallengeEntity.recordFailure` → `LOCKED` at `MfaProperties.maximumAttempts` (default 5, bounded 1..20). Compare: `TotpService.constantTimeEquals`. Audit: failure recorded on the challenge + account. |
| 5 | Recovery/disable/reset MFA require step-up / safe recovery | **MATCHED (INFERENCE)** | `RecoveryCodeEntity` + `regenerateRecoveryCodes`, `disableMyMfa`, `resetUserMfa` (admin) exist as distinct ops via `CredentialAdministrationService`. Step-up gating is present via challenge; confirm each sensitive op requires a fresh challenge (INFERENCE). |
| 6 | Ordinary admin cannot modify/delete/demote same-level admin | **GAP / UPDATE** | No explicit peer-protection guard found in `administration`/`authorization` services (only platform-role delegation is blocked). **UPDATE-ID-01.** |
| 7 | Cannot create a second Super Admin via ordinary API | **UNKNOWN / UPDATE** | No explicit "reject second super admin" invariant found; role-grant goes through `RoleManagementService` with platform-role delegation blocked, but a dedicated single-super-admin guard is not evident. **UPDATE-ID-02** (verify + add guard/test). |
| 8 | Sensitive role/MFA ops have before/after audit | **MATCHED (INFERENCE)** | Outbox-backed event recording present (`IdentityOutboxEntity`, `IdentityEventRecorder`); confirm before/after snapshot on role/MFA mutations (INFERENCE). |
| 9 | JWT checks issuer, audience, expiry, authority, tenant | **MATCHED** | `SmartFarmJwtValidator`: issuer via `createDefaultWithIssuer`, **audience set-match** (`audience_mismatch` fail), **explicit `missing_expiry` rejection**. Authorities/tenant resolved downstream (`GrpcSecurityContext`, `FarmScopeService`). |
| 10 | gRPC interceptor enforces same permission semantics as REST | **MATCHED** | `JwtServerInterceptor` + `GrpcMethodPolicy` per-method scopes; gateway `@PreAuthorize(SCOPE_...)` mirrors the gRPC method policies (cross-checked in `04-gateway-contract-coverage.md`). |
| 11 | MQTT command: envelope/signature, topic-tenant, issuedAt, schema, dedup | **MATCHED (strong)** | HMAC-SHA256 envelope (`MqttSecurityVerifier`, topic-bound, constant-time, default-off toggles); `IdentityMqttCommandTopic` parses tenant/type/version; durable inbox `IdentityMqttInboxEntity` dedupes. |
| 12 | Secrets/passwords/TOTP seed protected, not in docs/scripts/sample logs | **MATCHED w/ dev-note** | TOTP seed AES-GCM encrypted at rest; prod secrets env-driven. Dev defaults (`dev-*-secret`) are committed in `application-dev.yml` — dev-only, env-overridable (tracked in script register, not an identity defect). |

## 2. Findings requiring action (UPDATE items — no rewrite)

- **RISK-1 / UPDATE-ID-03 — no production super-admin bootstrap.**
  `DevSuperAdminBootstrap` is `dev & !prod` only. A prod deployment has **no documented
  way to create the first super admin** (manual DB seed? one-time migration? ops
  runbook?). This is a deployment-blocking gap for a real launch. Decide and document
  the prod bootstrap mechanism; do **not** make the dev bootstrap prod-active (that
  would ship a known password path). Feeds Phase 9/10.
- **UPDATE-ID-01 — admin peer-protection guard.** Add + test the invariant that an
  ordinary tenant admin cannot modify/demote a same-level or higher admin.
- **UPDATE-ID-02 — single-super-admin invariant.** Verify no ordinary API path can
  mint a second `SUPER_ADMIN`; add an explicit guard + test if absent.
- **(items 2,5,8 INFERENCE) — enforcement-point tests.** The flows exist; add targeted
  tests proving (a) privileged op blocked pre-enrollment, (b) each MFA-sensitive op
  requires a fresh step-up challenge, (c) before/after audit on role/MFA mutations.

## 3. KEEP (do not touch — stronger than backlog)

- TOTP monotonic replay guard (`lastAcceptedTimeStep`), constant-time compare,
  AES-GCM secret-at-rest, challenge lockout, account lockout on failed login.
- HMAC-SHA256 MQTT envelope with topic binding + default-off toggles (ISSUE-01/02).
- JWT issuer+audience+expiry validation; gRPC method policy mirroring REST scopes.
- FarmDirectory **fail-closed** fallback — a *security feature* (fails access shut when
  the farm registry is unbuilt). Keep it; removing it would fail open. (See DOC-02 in
  `06-cleanup-register.md`.)

**Net Phase 4 verdict:** identity security is **conformant and in several places
stronger than backlog** — KEEP the mechanisms. The only real gaps are operational
(prod super-admin bootstrap) and two admin-invariant guards/tests (peer protection,
single-super-admin). None justify rewriting identity; all are scoped UPDATE items for
the Phase 5 register. **No security downgrade is proposed anywhere.**
