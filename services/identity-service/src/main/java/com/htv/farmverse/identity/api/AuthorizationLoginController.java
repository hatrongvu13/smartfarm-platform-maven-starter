package com.htv.farmverse.identity.api;

import java.util.List;

import com.htv.farmverse.identity.mfa.application.model.TotpEnrollment;
import com.htv.farmverse.identity.oauth2.AuthService;
import com.htv.farmverse.identity.oauth2.AuthorizationSessionService;
import com.htv.farmverse.identity.oauth2.IdentityAuthorizationPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthorizationLoginController {

    private static final String AUTHENTICATION = "identity.oauth.login";
    private static final String ENROLLMENT = "identity.oauth.enrollment";
    private static final String RECOVERY_CODES = "identity.oauth.recovery-codes";

    private final AuthService auth;
    private final AuthorizationSessionService authorizationSessions;
    private final SavedRequestAwareAuthenticationSuccessHandler successHandler =
            new SavedRequestAwareAuthenticationSuccessHandler();

    public AuthorizationLoginController(
            AuthService auth,
            AuthorizationSessionService authorizationSessions
    ) {
        this.auth = auth;
        this.authorizationSessions = authorizationSessions;
        successHandler.setDefaultTargetUrl("/");
    }

    @GetMapping(value = "/oauth/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> loginPage(CsrfToken csrf) {
        return html(HttpStatus.OK, loginForm(csrf, null, List.of()));
    }

    @PostMapping(value = "/oauth/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> login(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false) String tenantId,
            CsrfToken csrf,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        try {
            AuthService.AuthorizationLoginResponse result =
                    auth.loginForAuthorization(tenantId, email, password);
            if (AuthService.TENANT_SELECTION_REQUIRED.equals(
                    result.authenticationStatus()
            )) {
                return html(
                        HttpStatus.OK,
                        loginForm(csrf, email, result.tenants())
                );
            }
            if (!AuthService.COMPLETED.equals(result.authenticationStatus())) {
                request.getSession(true).setAttribute(AUTHENTICATION, result);
                if (AuthService.MFA_ENROLLMENT_REQUIRED.equals(
                        result.authenticationStatus()
                )) {
                    return html(HttpStatus.OK, enrollmentStartForm(csrf));
                }
                return html(HttpStatus.OK, mfaForm(csrf, result.availableMethods()));
            }
            return authenticated(result, request, response);
        } catch (ResponseStatusException exception) {
            return html(
                    HttpStatus.UNAUTHORIZED,
                    loginForm(csrf, null, List.of())
            );
        }
    }

    @PostMapping(value = "/oauth/login/mfa", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyMfa(
            @RequestParam String method,
            @RequestParam String code,
            CsrfToken csrf,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthService.AuthorizationLoginResponse pending = pending(
                request.getSession(false),
                AuthService.MFA_REQUIRED
        );
        try {
            AuthService.AuthorizationLoginResponse result =
                    auth.verifyMfaForAuthorization(
                            pending.challengeToken(),
                            method,
                            code
                    );
            removePending(request);
            return authenticated(result, request, response);
        } catch (ResponseStatusException exception) {
            return html(
                    HttpStatus.UNAUTHORIZED,
                    mfaForm(csrf, pending.availableMethods())
            );
        }
    }

    @PostMapping(value = "/oauth/login/mfa/enroll", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> beginEnrollment(
            CsrfToken csrf,
            HttpServletRequest request
    ) {
        AuthService.AuthorizationLoginResponse pending = pending(
                request.getSession(false),
                AuthService.MFA_ENROLLMENT_REQUIRED
        );
        try {
            TotpEnrollment enrollment = auth.beginRequiredTotpEnrollment(
                    pending.challengeToken(),
                    "FarmVerse OIDC sign-in"
            );
            request.getSession(false).setAttribute(ENROLLMENT, enrollment);
            return html(HttpStatus.OK, enrollmentForm(csrf, enrollment));
        } catch (ResponseStatusException exception) {
            removePending(request);
            return html(
                    HttpStatus.UNAUTHORIZED,
                    loginForm(csrf, null, List.of())
            );
        }
    }

    @PostMapping(
            value = "/oauth/login/mfa/enroll/confirm",
            produces = MediaType.TEXT_HTML_VALUE
    )
    public ResponseEntity<String> confirmEnrollment(
            @RequestParam String code,
            CsrfToken csrf,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        HttpSession session = request.getSession(false);
        AuthService.AuthorizationLoginResponse pending = pending(
                session,
                AuthService.MFA_ENROLLMENT_REQUIRED
        );
        TotpEnrollment enrollment = session == null
                ? null
                : (TotpEnrollment) session.getAttribute(ENROLLMENT);
        if (enrollment == null) {
            removePending(request);
            return html(
                    HttpStatus.UNAUTHORIZED,
                    loginForm(csrf, null, List.of())
            );
        }
        try {
            AuthService.AuthorizationEnrollmentResponse result =
                    auth.confirmRequiredTotpEnrollmentForAuthorization(
                            pending.challengeToken(),
                            enrollment.authenticatorId(),
                            code
                    );
            removePending(request);
            request.getSession().setAttribute(
                    RECOVERY_CODES,
                    result.recoveryCodes()
            );
            storeAuthentication(result.authentication(), request, response);
            return html(
                    HttpStatus.OK,
                    recoveryCodesPage(csrf, result.recoveryCodes())
            );
        } catch (ResponseStatusException exception) {
            return html(HttpStatus.UNAUTHORIZED, enrollmentForm(csrf, enrollment));
        }
    }

    @PostMapping(value = "/oauth/login/continue", produces = MediaType.TEXT_HTML_VALUE)
    public void continueAfterEnrollment(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null
                || session.getAttribute(RECOVERY_CODES) == null
                || SecurityContextHolder.getContext().getAuthentication() == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        session.removeAttribute(RECOVERY_CODES);
        successHandler.onAuthenticationSuccess(
                request,
                response,
                SecurityContextHolder.getContext().getAuthentication()
        );
    }

    private ResponseEntity<String> authenticated(
            AuthService.AuthorizationLoginResponse result,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        storeAuthentication(result, request, response);
        try {
            successHandler.onAuthenticationSuccess(
                    request,
                    response,
                    SecurityContextHolder.getContext().getAuthentication()
            );
            return html(HttpStatus.FOUND, "");
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not continue OAuth authorization",
                    exception
            );
        }
    }

    private void storeAuthentication(
            AuthService.AuthorizationLoginResponse result,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (!AuthService.COMPLETED.equals(result.authenticationStatus())
                || result.subjectId() == null
                || result.tenantId() == null
                || result.authenticatedAt() == null) {
            throw new IllegalArgumentException(
                    "Authorization login result is incomplete"
            );
        }
        IdentityAuthorizationPrincipal principal =
                new IdentityAuthorizationPrincipal(
                        result.subjectId(),
                        result.tenantId(),
                        result.authenticationMethods(),
                        result.authenticatedAt()
                );
        authorizationSessions.authenticate(principal, request, response);
        HttpSession session = request.getSession(false);
        session.removeAttribute(AUTHENTICATION);
        session.removeAttribute(ENROLLMENT);
    }

    private static AuthService.AuthorizationLoginResponse pending(
            HttpSession session,
            String expectedStatus
    ) {
        Object value = session == null ? null : session.getAttribute(AUTHENTICATION);
        if (!(value instanceof AuthService.AuthorizationLoginResponse pending)
                || !expectedStatus.equals(pending.authenticationStatus())
                || pending.challengeToken() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication challenge expired"
            );
        }
        return pending;
    }

    private static void removePending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(AUTHENTICATION);
            session.removeAttribute(ENROLLMENT);
        }
    }

    private static String loginForm(
            CsrfToken csrf,
            String email,
            List<AuthService.TenantOption> tenants
    ) {
        StringBuilder tenantOptions = new StringBuilder();
        for (AuthService.TenantOption tenant : tenants) {
            tenantOptions.append("<option value=\"")
                    .append(escape(tenant.tenantId()))
                    .append("\">")
                    .append(escape(tenant.tenantName()))
                    .append("</option>");
        }
        String tenantField = tenants.isEmpty()
                ? "<label>Tenant ID (if required)<input name=\"tenantId\" autocomplete=\"organization\"></label>"
                : "<label>Choose tenant<select name=\"tenantId\" required>"
                        + tenantOptions + "</select></label>";
        return page("Sign in", "<form method=\"post\" action=\"/oauth/login\">"
                + csrf(csrf)
                + "<label>Email<input name=\"email\" type=\"email\" autocomplete=\"username\" value=\""
                + escape(email == null ? "" : email)
                + "\" required></label>"
                + "<label>Password<input name=\"password\" type=\"password\" autocomplete=\"current-password\" required></label>"
                + tenantField
                + "<button type=\"submit\">Continue</button></form>");
    }

    private static String mfaForm(CsrfToken csrf, List<String> methods) {
        StringBuilder options = new StringBuilder();
        for (String method : methods) {
            options.append("<option value=\"")
                    .append(escape(method))
                    .append("\">")
                    .append(escape(method))
                    .append("</option>");
        }
        return page("Multi-factor verification",
                "<form method=\"post\" action=\"/oauth/login/mfa\">"
                        + csrf(csrf)
                        + "<label>Method<select name=\"method\" required>"
                        + options
                        + "</select></label>"
                        + "<label>Code<input name=\"code\" inputmode=\"numeric\" autocomplete=\"one-time-code\" required></label>"
                        + "<button type=\"submit\">Verify</button></form>");
    }

    private static String enrollmentStartForm(CsrfToken csrf) {
        return page("Multi-factor enrollment required",
                "<form method=\"post\" action=\"/oauth/login/mfa/enroll\">"
                        + csrf(csrf)
                        + "<button type=\"submit\">Begin TOTP enrollment</button></form>");
    }

    private static String enrollmentForm(
            CsrfToken csrf,
            TotpEnrollment enrollment
    ) {
        return page("Set up authenticator",
                "<p>Add this TOTP URI to your authenticator app:</p><code>"
                        + escape(enrollment.otpauthUri())
                        + "</code><form method=\"post\" action=\"/oauth/login/mfa/enroll/confirm\">"
                        + csrf(csrf)
                        + "<label>Authenticator code<input name=\"code\" inputmode=\"numeric\" autocomplete=\"one-time-code\" required></label>"
                        + "<button type=\"submit\">Confirm enrollment</button></form>");
    }

    private static String recoveryCodesPage(
            CsrfToken csrf,
            List<String> recoveryCodes
    ) {
        String codes = recoveryCodes.stream()
                .map(AuthorizationLoginController::escape)
                .reduce((left, right) -> left + "<br>" + right)
                .orElse("");
        return page("Save recovery codes",
                "<p>Store these codes securely. They will not be shown again.</p><code>"
                        + codes
                        + "</code><form method=\"post\" action=\"/oauth/login/continue\">"
                        + csrf(csrf)
                        + "<button type=\"submit\">Continue sign-in</button></form>");
    }

    private static String csrf(CsrfToken csrf) {
        return "<input type=\"hidden\" name=\"" + escape(csrf.getParameterName())
                + "\" value=\"" + escape(csrf.getToken()) + "\">";
    }

    private static String page(String title, String body) {
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escape(title) + "</title></head><body><main><h1>"
                + escape(title) + "</h1>" + body + "</main></body></html>";
    }

    private static ResponseEntity<String> html(HttpStatus status, String content) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .body(content);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
