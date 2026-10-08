package com.htv.smartfarm.gateway.graphql;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.FieldMask;
import com.htv.smartfarm.gateway.identity.GatewayRequestContextFactory;
import com.htv.smartfarm.gateway.identity.ServiceTokenClient;
import com.htv.smartfarm.proto.common.v1.PageRequest;
import com.htv.smartfarm.proto.common.v1.RequestContext;
import com.htv.smartfarm.proto.identity.v1.*;
import com.htv.smartfarm.security.grpc.BearerCallCredentials;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Controller
public class IdentityGraphQlController {

    private final IdentityDirectoryServiceGrpc.IdentityDirectoryServiceBlockingStub directory;
    private final IdentityAdministrationServiceGrpc.IdentityAdministrationServiceBlockingStub admin;
    private final IdentityCredentialServiceGrpc.IdentityCredentialServiceBlockingStub credential;
    private final ServiceTokenClient tokens;
    private final GatewayRequestContextFactory contexts;
    private final String audience;
    private final long deadlineMillis;

    public IdentityGraphQlController(
            IdentityDirectoryServiceGrpc.IdentityDirectoryServiceBlockingStub directory,
            IdentityAdministrationServiceGrpc.IdentityAdministrationServiceBlockingStub admin,
            IdentityCredentialServiceGrpc.IdentityCredentialServiceBlockingStub credential,
            ServiceTokenClient tokens,
            GatewayRequestContextFactory contexts,
            @Value("${smartfarm.identity.grpc-audience:smartfarm-identity}") String audience,
            @Value("${smartfarm.gateway.grpc.identity-deadline:5s}") java.time.Duration deadline
    ) {
        this.directory = directory;
        this.admin = admin;
        this.credential = credential;
        this.tokens = tokens;
        this.contexts = contexts;
        this.audience = audience;
        if (deadline == null || deadline.isZero() || deadline.isNegative())
            throw new IllegalArgumentException("identity deadline must be positive");
        this.deadlineMillis = deadline.toMillis();
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:principal:read')")
    public Mono<Map<String, Object>> me() {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            return principal(
                    directory(jwt, context)
                            .getPrincipal(
                                    GetPrincipalRequest.newBuilder()
                                            .setContext(context)
                                            .build()
                            )
                            .getPrincipal()
            );
        });
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:security:read')")
    public Mono<Map<String, Object>> mySecurityProfile() {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            return securityProfile(
                    credential(jwt, context)
                            .getSecurityProfile(
                                    GetSecurityProfileRequest.newBuilder()
                                            .setContext(context)
                                            .setSubjectId(jwt.getSubject())
                                            .build()
                            )
                            .getSecurityProfile()
            );
        });
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:user:read')")
    public Mono<Map<String, Object>> users(
            @Argument Map<String, Object> filter,
            @Argument Map<String, Object> page
    ) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            ListUsersRequest.Builder request = ListUsersRequest.newBuilder()
                    .setContext(context)
                    .setPage(page(page));

            if (filter != null) {
                put(filter, "searchText", request::setSearchText);
                put(filter, "roleCode", request::setRoleCode);
            }

            ListUsersResponse response = admin(jwt, context)
                    .listUsers(request.build());

            return connection(
                    response.getUsersList().stream()
                            .map(IdentityGraphQlController::user)
                            .toList(),
                    response.getPage().getNextPageToken()
            );
        });
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:user:read')")
    public Mono<Map<String, Object>> userAuthorization(
            @Argument String subjectId
    ) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            return authorization(
                    admin(jwt, context)
                            .getUserAuthorization(
                                    GetUserAuthorizationRequest.newBuilder()
                                            .setContext(context)
                                            .setSubjectId(
                                                    required(subjectId, "subjectId")
                                            )
                                            .build()
                            )
                            .getAuthorization()
            );
        });
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:role:read')")
    public Mono<Map<String, Object>> roles(
            @Argument Map<String, Object> page
    ) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            ListRolesResponse response = admin(jwt, context)
                    .listRoles(
                            ListRolesRequest.newBuilder()
                                    .setContext(context)
                                    .setPage(page(page))
                                    .setIncludePermissions(true)
                                    .build()
                    );

            return connection(
                    response.getRolesList().stream()
                            .map(IdentityGraphQlController::role)
                            .toList(),
                    response.getPage().getNextPageToken()
            );
        });
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:permission:read')")
    public Mono<Map<String, Object>> permissions(
            @Argument Map<String, Object> page
    ) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            ListPermissionsResponse response = admin(jwt, context)
                    .listPermissions(
                            ListPermissionsRequest.newBuilder()
                                    .setContext(context)
                                    .setPage(page(page))
                                    .build()
                    );

            return connection(
                    response.getPermissionsList().stream()
                            .map(IdentityGraphQlController::permission)
                            .toList(),
                    response.getPage().getNextPageToken()
            );
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:user:create')")
    public Mono<Map<String, Object>> createUser(
            @Argument Map<String, Object> input
    ) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            CreateUserRequest.Builder request = CreateUserRequest.newBuilder()
                    .setContext(context)
                    .setEmail(requiredInput(input, "email"))
                    .setInitialPassword(requiredInput(input, "initialPassword"))
                    .setDisplayName(requiredInput(input, "displayName"))
                    .setPhoneNumber(optionalInput(input, "phoneNumber"))
                    .setLocale(defaultInput(input, "locale", "vi-VN"))
                    .setTimeZone(defaultInput(input, "timeZone", "Asia/Ho_Chi_Minh"))
                    .setActivateImmediately(booleanInput(input, "activateImmediately", true));

            Object roleCodes = input == null ? null : input.get("initialRoleCodes");
            if (roleCodes instanceof List<?> values) {
                values.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .forEach(request::addInitialRoleCodes);
            }

            CreateUserResponse response = admin(jwt, context)
                    .createUser(request.build());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("subjectId", response.getSubjectId());
            result.put("membershipId", response.getMembershipId());
            result.put("tenantId", response.getTenantId());
            result.put("membershipStatus", enumName(response.getMembershipStatus().name()));
            result.put("existingAccount", response.getExistingAccount());
            return result;
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:principal:update')")
    public Mono<Map<String, Object>> updateMyProfile(
            @Argument Map<String, Object> input
    ) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            UpdatePrincipalProfileRequest.Builder request =
                    UpdatePrincipalProfileRequest.newBuilder()
                            .setContext(context)
                            .setSubjectId(jwt.getSubject())
                            .setExpectedVersion(
                                    requiredLong(input, "expectedVersion")
                            );

            PrincipalProfile.Builder profile = PrincipalProfile.newBuilder();
            FieldMask.Builder mask = FieldMask.newBuilder();

            addProfileField(
                    input,
                    "displayName",
                    "display_name",
                    profile::setDisplayName,
                    mask
            );
            addProfileField(
                    input,
                    "firstName",
                    "first_name",
                    profile::setFirstName,
                    mask
            );
            addProfileField(
                    input,
                    "lastName",
                    "last_name",
                    profile::setLastName,
                    mask
            );
            addProfileField(
                    input,
                    "phoneNumber",
                    "phone_number",
                    profile::setPhoneNumber,
                    mask
            );
            addProfileField(
                    input,
                    "avatarUrl",
                    "avatar_url",
                    profile::setAvatarUrl,
                    mask
            );
            addProfileField(
                    input,
                    "locale",
                    "locale",
                    profile::setLocale,
                    mask
            );
            addProfileField(
                    input,
                    "timeZone",
                    "time_zone",
                    profile::setTimeZone,
                    mask
            );

            if (mask.getPathsCount() == 0) {
                throw new IllegalArgumentException(
                        "At least one profile field is required"
                );
            }

            request.setProfile(profile).setUpdateMask(mask);

            return principal(
                    directory(jwt, context)
                            .updatePrincipalProfile(request.build())
                            .getPrincipal()
            );
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:mfa:enroll')")
    public Mono<Map<String, Object>> beginTotpEnrollment(
            @Argument String displayName
    ) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            BeginTotpEnrollmentResponse response =
                    credential(jwt, context)
                            .beginTotpEnrollment(
                                    BeginTotpEnrollmentRequest.newBuilder()
                                            .setContext(context)
                                            .setSubjectId(jwt.getSubject())
                                            .setDisplayName(safe(displayName))
                                            .build()
                            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("authenticatorId", response.getAuthenticatorId());
            result.put("otpauthUri", response.getOtpauthUri());
            result.put("expiresAt", ts(response.getExpiresAt()));
            return result;
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:mfa:enroll')")
    public Mono<Map<String, Object>> confirmTotpEnrollment(
            @Argument String authenticatorId,
            @Argument String code
    ) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            ConfirmTotpEnrollmentResponse response =
                    credential(jwt, context)
                            .confirmTotpEnrollment(
                                    ConfirmTotpEnrollmentRequest.newBuilder()
                                            .setContext(context)
                                            .setSubjectId(jwt.getSubject())
                                            .setAuthenticatorId(
                                                    required(
                                                            authenticatorId,
                                                            "authenticatorId"
                                                    )
                                            )
                                            .setCode(required(code, "code"))
                                            .build()
                            );

            return Map.of(
                    "authenticatorId",
                    response.getAuthenticatorId(),
                    "recoveryCodes",
                    response.getRecoveryCodesList()
            );
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:mfa:disable')")
    public Mono<Boolean> disableMyMfa(@Argument String authenticatorId) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            return credential(jwt, context)
                    .disableOwnMfa(
                            DisableOwnMfaRequest.newBuilder()
                                    .setContext(context)
                                    .setSubjectId(jwt.getSubject())
                                    .setAuthenticatorId(
                                            required(
                                                    authenticatorId,
                                                    "authenticatorId"
                                            )
                                    )
                                    .build()
                    )
                    .getDisabled();
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:mfa:recovery:regenerate')")
    public Mono<Map<String, Object>> regenerateRecoveryCodes(
            @Argument String authenticatorId
    ) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            RegenerateRecoveryCodesResponse response =
                    credential(jwt, context)
                            .regenerateRecoveryCodes(
                                    RegenerateRecoveryCodesRequest.newBuilder()
                                            .setContext(context)
                                            .setSubjectId(jwt.getSubject())
                                            .setAuthenticatorId(
                                                    required(
                                                            authenticatorId,
                                                            "authenticatorId"
                                                    )
                                            )
                                            .build()
                            );

            return Map.of(
                    "recoveryCodes",
                    response.getRecoveryCodesList()
            );
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:user:credential:reset')")
    public Mono<Boolean> changeMyPassword(
            @Argument String currentPassword,
            @Argument String newPassword
    ) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            return credential(jwt, context)
                    .changeOwnPassword(
                            ChangeOwnPasswordRequest.newBuilder()
                                    .setContext(context)
                                    .setSubjectId(jwt.getSubject())
                                    .setCurrentPassword(
                                            required(
                                                    currentPassword,
                                                    "currentPassword"
                                            )
                                    )
                                    .setNewPassword(
                                            required(
                                                    newPassword,
                                                    "newPassword"
                                            )
                                    )
                                    .build()
                    )
                    .getChanged();
        });
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('SCOPE_identity:user:mfa:reset')")
    public Mono<Boolean> resetUserMfa(@Argument String subjectId) {
        return jwtCall(jwt -> {
            RequestContext context = contexts.create(jwt);

            return credential(jwt, context)
                    .resetUserMfa(
                            ResetUserMfaRequest.newBuilder()
                                    .setContext(context)
                                    .setSubjectId(
                                            required(subjectId, "subjectId")
                                    )
                                    .build()
                    )
                    .getReset();
        });
    }

    private <T> Mono<T> jwtCall(java.util.function.Function<Jwt, T> action) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> (Jwt) context.getAuthentication().getPrincipal())
                .map(action)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private BearerCallCredentials credentials(
            Jwt jwt,
            RequestContext context
    ) {
        String tenantId = required(
                jwt.getClaimAsString("tenant_id"),
                "tenantId"
        );

        String correlationId = required(
                context.getCorrelationId(),
                "correlationId"
        );

        return new BearerCallCredentials(
                () -> tokens.tokenFor(
                        audience,
                        tenantId,
                        jwt.getSubject()
                ),
                () -> tenantId,
                () -> correlationId
        );
    }

    private IdentityDirectoryServiceGrpc.IdentityDirectoryServiceBlockingStub directory(
            Jwt jwt,
            RequestContext context
    ) {
        return directory
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .withCallCredentials(credentials(jwt, context));
    }

    private IdentityAdministrationServiceGrpc.IdentityAdministrationServiceBlockingStub admin(
            Jwt jwt,
            RequestContext context
    ) {
        return admin
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .withCallCredentials(credentials(jwt, context));
    }

    private IdentityCredentialServiceGrpc.IdentityCredentialServiceBlockingStub credential(
            Jwt jwt,
            RequestContext context
    ) {
        return credential
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
                .withCallCredentials(credentials(jwt, context));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static PageRequest page(Map<String, Object> input) {
        int size = input != null && input.get("size") instanceof Number number
                ? number.intValue()
                : 20;
        String token = input == null || input.get("token") == null
                ? ""
                : String.valueOf(input.get("token"));
        return PageRequest.newBuilder()
                .setPageSize(size)
                .setPageToken(token)
                .build();
    }

    private static void put(
            Map<String, Object> source,
            String key,
            java.util.function.Consumer<String> consumer
    ) {
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            consumer.accept(String.valueOf(value));
        }
    }

    private static void addProfileField(
            Map<String, Object> source,
            String inputName,
            String protoName,
            java.util.function.Consumer<String> setter,
            FieldMask.Builder mask
    ) {
        if (source != null && source.containsKey(inputName)) {
            Object value = source.get(inputName);
            setter.accept(value == null ? "" : String.valueOf(value));
            mask.addPaths(protoName);
        }
    }

    private static String requiredInput(Map<String, Object> input, String field) {
        if (input == null || input.get(field) == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return required(String.valueOf(input.get(field)), field);
    }

    private static String optionalInput(Map<String, Object> input, String field) {
        if (input == null || input.get(field) == null) return "";
        return String.valueOf(input.get(field)).trim();
    }

    private static String defaultInput(
            Map<String, Object> input,
            String field,
            String defaultValue
    ) {
        String value = optionalInput(input, field);
        return value.isEmpty() ? defaultValue : value;
    }

    private static boolean booleanInput(
            Map<String, Object> input,
            String field,
            boolean defaultValue
    ) {
        if (input == null || input.get(field) == null) return defaultValue;
        Object value = input.get(field);
        if (value instanceof Boolean booleanValue) return booleanValue;
        throw new IllegalArgumentException(field + " must be a boolean");
    }

    private static long requiredLong(Map<String, Object> input, String field) {
        if (input == null || !(input.get(field) instanceof Number number)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return number.longValue();
    }

    private static Map<String, Object> connection(List<?> nodes, String next) {
        return Map.of("nodes", nodes, "nextPageToken", next);
    }

    private static String enumName(String name) {
        int index = name.lastIndexOf('_');
        return index < 0 ? name : name.substring(index + 1);
    }

    private static String ts(com.google.protobuf.Timestamp value) {
        return Instant.ofEpochSecond(
                value.getSeconds(),
                value.getNanos()
        ).toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, Object> permission(PermissionInfo value) {
        return Map.of(
                "code", value.getCode(),
                "resourceType", value.getResourceType(),
                "action", value.getAction(),
                "description", value.getDescription()
        );
    }

    private static Map<String, Object> role(RoleInfo value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleId", value.getRoleId());
        result.put("tenantId", value.getTenantId());
        result.put("code", value.getCode());
        result.put("name", value.getName());
        result.put("type", enumName(value.getType().name()));
        result.put("permissions", value.getPermissionsList().stream()
                .map(IdentityGraphQlController::permission).toList());
        result.put("createdAt", ts(value.getCreatedAt()));
        result.put("updatedAt", ts(value.getUpdatedAt()));
        result.put("version", (double) value.getVersion());
        return result;
    }

    private static Map<String, Object> user(UserSummary value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subjectId", value.getSubjectId());
        result.put("membershipId", value.getMembershipId());
        result.put("tenantId", value.getTenantId());
        result.put("email", value.getEmail());
        result.put("displayName", value.getDisplayName());
        result.put("accountStatus", enumName(value.getAccountStatus().name()));
        result.put("membershipStatus", enumName(value.getMembershipStatus().name()));
        result.put("roles", value.getRolesList());
        result.put("createdAt", ts(value.getCreatedAt()));
        result.put("updatedAt", ts(value.getUpdatedAt()));
        return result;
    }

    private static Map<String, Object> authorization(UserAuthorization value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subjectId", value.getSubjectId());
        result.put("membershipId", value.getMembershipId());
        result.put("tenantId", value.getTenantId());
        result.put("accountStatus", enumName(value.getAccountStatus().name()));
        result.put("membershipStatus", enumName(value.getMembershipStatus().name()));
        result.put("roles", value.getRolesList().stream()
                .map(IdentityGraphQlController::role).toList());
        result.put("effectivePermissions", value.getEffectivePermissionsList().stream()
                .map(IdentityGraphQlController::permission).toList());
        result.put("farmIds", value.getFarmIdsList());
        return result;
    }

    private static Map<String, Object> principal(Principal value) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("displayName", value.getProfile().getDisplayName());
        profile.put("email", value.getProfile().getEmail());
        profile.put("firstName", emptyToNull(value.getProfile().getFirstName()));
        profile.put("lastName", emptyToNull(value.getProfile().getLastName()));
        profile.put("phoneNumber", emptyToNull(value.getProfile().getPhoneNumber()));
        profile.put("avatarUrl", emptyToNull(value.getProfile().getAvatarUrl()));
        profile.put("locale", value.getProfile().getLocale());
        profile.put("timeZone", value.getProfile().getTimeZone());
        profile.put("emailVerified", value.getProfile().getEmailVerified());
        profile.put("phoneVerified", value.getProfile().getPhoneVerified());

        Map<String, Object> membership = new LinkedHashMap<>();
        membership.put("membershipId", value.getMembership().getMembershipId());
        membership.put("tenantId", value.getMembership().getTenantId());
        membership.put("status", enumName(value.getMembership().getStatus().name()));
        membership.put("roles", value.getMembership().getRolesList());
        membership.put("farmIds", value.getMembership().getFarmIdsList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subjectId", value.getSubjectId());
        result.put("active", value.getActive());
        result.put("version", (double) value.getVersion());
        result.put("profile", profile);
        result.put("membership", membership);
        result.put("createdAt", ts(value.getCreatedAt()));
        result.put("updatedAt", ts(value.getUpdatedAt()));
        return result;
    }

    private static Map<String, Object> securityProfile(SecurityProfileInfo value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subjectId", value.getSubjectId());
        result.put("totpRequired", value.getTotpRequired());
        result.put("totpEnabled", value.getTotpEnabled());
        result.put("authenticators", value.getAuthenticatorsList().stream()
                .map(IdentityGraphQlController::authenticator)
                .toList());
        return result;
    }

    private static Map<String, Object> authenticator(AuthenticatorInfo value) {
        return Map.of(
                "authenticatorId", value.getAuthenticatorId(),
                "type", value.getType(),
                "status", value.getStatus(),
                "displayName", value.getDisplayName()
        );
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
