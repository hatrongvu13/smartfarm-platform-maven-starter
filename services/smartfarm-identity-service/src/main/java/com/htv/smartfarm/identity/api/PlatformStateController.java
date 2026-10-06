package com.htv.smartfarm.identity.api;

import java.util.Map;

import com.htv.smartfarm.identity.bootstrap.SuperAdminBootstrapService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, pre-login platform state for the front-end.
 *
 * <p>A browser-facing (public) query — explicitly in the REST tier, like health/actuator and the
 * auth endpoints — that lets the FE decide whether to show the "create first super-admin" screen.
 * It exposes NO tenant/business data: only whether the system has been seeded yet.</p>
 *
 * <p>{@code GET /api/v1/platform/deployment-state} ->
 * {@code {"initialized": <bool>, "superAdminExists": <bool>, "bootstrapRequired": <bool>}}.
 * When {@code bootstrapRequired} is true the FE should route to the one-time
 * {@code POST /api/v1/auth/bootstrap-superadmin} flow.</p>
 */
@RestController
public class PlatformStateController {

    private final SuperAdminBootstrapService bootstrap;

    public PlatformStateController(SuperAdminBootstrapService bootstrap) {
        this.bootstrap = bootstrap;
    }

    @GetMapping(
            value = "/api/v1/platform/deployment-state",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Boolean> deploymentState() {
        boolean superAdminExists = bootstrap.superAdminExists();
        return Map.of(
                "initialized", superAdminExists,
                "superAdminExists", superAdminExists,
                "bootstrapRequired", !superAdminExists
        );
    }
}
