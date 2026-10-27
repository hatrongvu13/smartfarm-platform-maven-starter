package com.htv.farmverse.service.mtls;

import java.io.IOException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lớp mTLS thứ hai: TLS đã chứng minh cert do CA của ta ký; filter này kiểm <b>ai</b> đang gọi bằng
 * SAN {@code spiffe://farmverse/<service>} và chỉ cho danh sách được phép.
 * Chạy trước Spring Security; JWT vẫn quyết định người dùng được làm gì.
 *
 * <p>{@code protectedPaths} cho phép áp dụng một phần (identity: chỉ đường máy-gọi-máy, còn trang login
 * thì trình duyệt vào không cần cert).
 */
public class ServiceIdentityFilter extends OncePerRequestFilter {

    public static final String SPIFFE_PREFIX = "spiffe://farmverse/";
    /** Thuộc tính request: id service đã xác minh, ví dụ {@code gateway}. */
    public static final String CALLER_ATTRIBUTE = ServiceIdentityFilter.class.getName() + ".caller";

    private static final String CERTIFICATES = "jakarta.servlet.request.X509Certificate";
    private static final int SAN_URI = 6;

    private final Set<String> allowedServices;
    private final Predicate<HttpServletRequest> protectedPaths;

    public ServiceIdentityFilter(Set<String> allowedServices, Predicate<HttpServletRequest> protectedPaths) {
        this.allowedServices = Set.copyOf(allowedServices);
        this.protectedPaths = protectedPaths;
    }

    /** Mọi request đều phải có cert của một service được phép. */
    public static ServiceIdentityFilter everyRequest(Set<String> allowedServices) {
        return new ServiceIdentityFilter(allowedServices, request -> true);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !protectedPaths.test(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> caller = callerOf(request);
        if (caller.isEmpty() || !allowedServices.contains(caller.get())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":403,\"title\":\"Forbidden\",\"code\":\"SF-MTLS-001\","
                    + "\"detail\":\"Cần chứng chỉ client của service được phép\"}");
            return;
        }
        request.setAttribute(CALLER_ATTRIBUTE, caller.get());
        chain.doFilter(request, response);
    }

    /** Id service trong SAN URI của cert client đầu tiên, nếu có. */
    public static Optional<String> callerOf(HttpServletRequest request) {
        Object attribute = request.getAttribute(CERTIFICATES);
        if (!(attribute instanceof X509Certificate[] chain) || chain.length == 0) {
            return Optional.empty();
        }
        return spiffeId(chain[0]);
    }

    static Optional<String> spiffeId(X509Certificate certificate) {
        try {
            Collection<List<?>> names = certificate.getSubjectAlternativeNames();
            if (names == null) {
                return Optional.empty();
            }
            return names.stream()
                    .filter(name -> name.size() == 2 && Integer.valueOf(SAN_URI).equals(name.get(0)))
                    .map(name -> String.valueOf(name.get(1)))
                    .filter(uri -> uri.startsWith(SPIFFE_PREFIX))
                    .map(uri -> uri.substring(SPIFFE_PREFIX.length()))
                    .findFirst();
        } catch (CertificateParsingException e) {
            return Optional.empty();
        }
    }
}
