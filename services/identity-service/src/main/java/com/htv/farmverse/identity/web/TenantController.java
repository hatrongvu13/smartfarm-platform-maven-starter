package com.htv.farmverse.identity.web;

import java.util.List;

import com.htv.farmverse.identity.tenant.Tenant;
import com.htv.farmverse.identity.tenant.TenantAdministrationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tenant (tổ chức). Chỉ đội nền tảng. */
@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantAdministrationService tenants;

    @GetMapping
    @PreAuthorize(Can.MANAGE_TENANTS)
    public List<TenantView> list() {
        return tenants.list().stream().map(TenantView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Can.MANAGE_TENANTS)
    public TenantView create(@Valid @RequestBody TenantInput body) {
        return TenantView.of(tenants.create(body.code(), body.name()));
    }

    public record TenantInput(@NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,99}") String code,
                              @NotBlank @Size(max = 200) String name) {
    }

    public record TenantView(String id, String code, String name, boolean enabled) {
        static TenantView of(Tenant tenant) {
            return new TenantView(tenant.getId(), tenant.getCode(), tenant.getName(), tenant.isEnabled());
        }
    }
}
