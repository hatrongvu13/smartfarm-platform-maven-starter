package com.htv.smartfarm.identity.api;

import com.htv.smartfarm.identity.token.ServiceTokenService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class InternalServiceTokenController {

    private final ServiceTokenService serviceTokens;

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
