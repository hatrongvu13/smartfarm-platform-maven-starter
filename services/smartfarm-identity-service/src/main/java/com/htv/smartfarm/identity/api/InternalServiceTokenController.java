package com.htv.smartfarm.identity.api;

import com.htv.smartfarm.identity.token.ServiceTokenService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Machine-to-machine service-token mint. The gateway (and other callers whitelisted under
 * {@code smartfarm.identity.service-token.clients}) POST their client secret here to obtain a
 * short-lived JWT scoped to a target audience, which they then attach to downstream gRPC calls.
 *
 * <p>Authentication is by the client secret IN THE BODY, not a Bearer token, so this path is
 * permitAll in the security chain ({@code ServletResourceSecurity} / {@code IdentityConfiguration}).
 * Without this controller the gateway's {@code ServiceTokenClient} POST was Secured by the filter
 * chain but matched no handler, so Spring forwarded to {@code /error} and returned a misleading
 * 401 — the root cause of the gateway's {@code UNAUTHENTICATED: Unable to apply gRPC credentials}.
 */
@RestController
public class InternalServiceTokenController {

    private final ServiceTokenService serviceTokens;

    public InternalServiceTokenController(ServiceTokenService serviceTokens) {
        this.serviceTokens = serviceTokens;
    }

    /** Request body posted by the gateway's ServiceTokenClient. */
    public record TokenRequest(
            String clientId,
            String secret,
            String audience,
            String tenantId,
            String actorId
    ) {
    }

    @PostMapping(
            value = "/internal/service-token",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.OK)
    public ServiceTokenService.Grant issue(@RequestBody TokenRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            return serviceTokens.issue(
                    request.clientId(),
                    request.secret(),
                    request.audience(),
                    request.tenantId(),
                    request.actorId()
            );
        } catch (SecurityException ex) {
            // Bad client credentials or a disallowed audience — do not leak which.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "service token denied");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
