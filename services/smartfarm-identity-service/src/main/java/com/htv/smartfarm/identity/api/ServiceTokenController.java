package com.htv.smartfarm.identity.api;

import com.htv.smartfarm.identity.token.ServiceTokenService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * INTERNAL machine-to-machine endpoint (client-credentials style, Hướng A). It is served
 * only on the identity service's loopback binding and is deliberately NOT under
 * {@code /api/v1/auth/**}, so the gateway does not proxy it to the outside world — a service
 * token must never be obtainable by an external client. Callers authenticate with a
 * {@code clientId}+{@code secret} pair and receive a short-lived token scoped to a single
 * downstream audience. The {@code actorId} is recorded for audit traceability only.
 */
@RestController
@RequestMapping("/internal/service-token")
public class ServiceTokenController {

    private final ServiceTokenService service;

    public ServiceTokenController(ServiceTokenService service) {
        this.service = service;
    }

    public record TokenRequest(String clientId, String secret, String audience, String tenantId, String actorId) {
    }

    @PostMapping
    public ServiceTokenService.Grant issue(@RequestBody TokenRequest r) {
        if (r == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body required");
        try {
            return service.issue(r.clientId(), r.secret(), r.audience(), r.tenantId(), r.actorId());
        } catch (SecurityException denied) {
            // Do not leak whether the client id, the secret, or the audience was the problem.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "service token request denied");
        } catch (IllegalArgumentException bad) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, bad.getMessage());
        }
    }
}
