package com.htv.farmverse.identity.web;

import java.util.Set;

import com.htv.farmverse.identity.tenant.MembershipStatus;
import com.htv.farmverse.identity.tenant.UserAdministrationService;
import com.htv.farmverse.identity.tenant.UserView;
import com.htv.farmverse.service.security.FarmVerseClaims;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Người dùng trong tenant của token. Không thao tác được tenant khác. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAdministrationService users;

    @GetMapping
    @PreAuthorize(Can.READ_USERS)
    public PagedModel<UserView> list(@AuthenticationPrincipal Jwt jwt, @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(users.list(tenant(jwt), pageable));
    }

    @GetMapping("/{userId}")
    @PreAuthorize(Can.READ_USERS)
    public UserView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId) {
        return users.get(tenant(jwt), userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Can.MANAGE_USERS)
    public UserView add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewUser body) {
        return users.add(tenant(jwt), body.email(), body.displayName(), body.password(),
                body.roles() == null ? Set.of() : body.roles());
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize(Can.MANAGE_USERS)
    public UserView replaceRoles(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId,
                                 @Valid @RequestBody RoleSet body) {
        return users.replaceRoles(tenant(jwt), userId, body.roles());
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize(Can.MANAGE_USERS)
    public UserView changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId,
                                 @Valid @RequestBody StatusChange body) {
        return users.changeStatus(tenant(jwt), jwt.getSubject(), userId, body.status());
    }

    @PostMapping("/{userId}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Can.MANAGE_USERS)
    public void unlock(@AuthenticationPrincipal Jwt jwt, @PathVariable String userId) {
        users.unlock(tenant(jwt), userId);
    }

    private static String tenant(Jwt jwt) {
        return jwt.getClaimAsString(FarmVerseClaims.TENANT_ID);
    }

    /** {@code password} chỉ cần khi email chưa có tài khoản. */
    public record NewUser(@NotBlank @Email String email,
                          @NotBlank @Size(max = 150) String displayName,
                          @Size(min = 12, max = 128) String password,
                          Set<String> roles) {

        @Override
        public String toString() {
            return "NewUser[email=" + email + ", password=[REDACTED]]";
        }
    }

    public record RoleSet(@NotNull Set<String> roles) {
    }

    public record StatusChange(@NotNull MembershipStatus status) {
    }
}
