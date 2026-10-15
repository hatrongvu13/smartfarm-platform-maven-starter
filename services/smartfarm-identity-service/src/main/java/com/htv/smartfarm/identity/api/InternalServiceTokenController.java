package com.htv.smartfarm.identity.api;

import com.htv.smartfarm.identity.token.ServiceTokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InternalServiceTokenController {

    private static final String INTERNAL_SECRET_HEADER =
            "X-Identity-Internal-Secret";

    private final ServiceTokenService serviceTokens;
    private final String internalApiSecret;

    public InternalServiceTokenController(
            ServiceTokenService serviceTokens,
            @Value("${smartfarm.identity.internal-api.secret:}") String internalApiSecret
    ) {
        this.serviceTokens = java.util.Objects.requireNonNull(serviceTokens);
        this.internalApiSecret = internalApiSecret == null ? "" : internalApiSecret;
    }

    /**
     * Request body posted by the gateway's ServiceTokenClient.
     */
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
    public ServiceTokenService.Grant issue(
            @RequestBody TokenRequest request,
            @RequestHeader(
                    name = INTERNAL_SECRET_HEADER,
                    required = false
            ) String suppliedInternalApiSecret
    ) {
        if (!hasValidInternalSecret(suppliedInternalApiSecret)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "service token denied"
            );
        }
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

    private boolean hasValidInternalSecret(String suppliedSecret) {
        return !internalApiSecret.isBlank()
                && suppliedSecret != null
                && MessageDigest.isEqual(
                        internalApiSecret.getBytes(StandardCharsets.UTF_8),
                        suppliedSecret.getBytes(StandardCharsets.UTF_8)
                );
    }
}
