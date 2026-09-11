package com.shardeya.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Layer 1 of the three-layer tenant isolation model (00-ARCHITECTURE.md
 * §2.2): validates the access JWT and binds {@link TenantContext} for the
 * request. Everything the request handler needs (org, role, permissions) is
 * read straight off the token — no DB round trip on the hot path. That means
 * a password-change/account-lock's {@code token_version} bump takes up to the
 * token's remaining 15-minute lifetime to take effect; an active per-request
 * DB check for immediate revocation is deliberately deferred (see CLAUDE.md
 * "Milestone 1" notes) since nothing in M1 needs faster-than-that revocation.
 *
 * <p>Runs before Spring MVC's dispatch, so a rejected request here can't go
 * through {@link GlobalExceptionHandler} — it has to write the RFC 9457 body
 * itself.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    // "/api/v1/docs" only covers springdoc's initial redirect entrypoint
    // (configured via springdoc.swagger-ui.path) -- the actual UI assets it
    // redirects to (the page itself, plus its JS/CSS) are always served
    // under the fixed "/api/v1/swagger-ui/**" path regardless of that
    // config, so without listing it separately here the redirect target
    // itself 401s before the page can ever render. Confirmed live: GET
    // /api/v1/docs correctly 302s to /api/v1/swagger-ui/index.html, but
    // following that redirect returned 401 "missing token" until this was
    // added -- the raw /api/v1/openapi JSON spec was unaffected the whole
    // time since it's a real, separately-covered prefix.
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/v1/auth/", "/actuator/", "/api/v1/openapi", "/api/v1/docs", "/api/v1/swagger-ui");

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public TenantContextFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Originally load-bearing (M1): CORS preflight used to be handled
        // only inside DispatcherServlet.doDispatch(), which this filter's
        // own rejection of an OPTIONS request (no real Authorization header
        // ever accompanies a preflight) would pre-empt entirely. Now that
        // CorsConfig registers a genuine CorsFilter bean ahead of this one
        // (HIGHEST_PRECEDENCE — see its own javadoc for the fuller story of
        // why that filter-vs-DispatcherServlet distinction matters beyond
        // just OPTIONS), that filter already fully owns and terminates a
        // real preflight before this class ever sees it. This check is kept
        // as a defensive backstop, not because it's still load-bearing today
        // — if filter ordering is ever changed such that this one runs
        // first again, this line is what stops the exact M1-era regression
        // from silently coming back.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        boolean isPublic = PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
        String token = extractBearerToken(request);

        try {
            if (token != null) {
                bindTenantFromToken(token);
            } else if (!isPublic) {
                writeUnauthorized(response, "error.auth.missingToken");
                return;
            }
            chain.doFilter(request, response);
        } catch (UnauthorizedException e) {
            writeUnauthorized(response, e.messageKey());
        } finally {
            TenantContext.clear();
        }
    }

    private void bindTenantFromToken(String token) {
        JwtService.AccessTokenClaims claims = jwtService.parseAndValidate(token);
        Set<String> permissions = claims.permissions() == null ? Set.of() : new HashSet<>(claims.permissions());
        TenantContext.set(new TenantContext.Tenant(
                claims.orgId(), claims.userId(), claims.orgType(), claims.roleCode(),
                permissions, claims.projectScope(), claims.allProjects()));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String messageKey) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Unauthorized");
        problem.setProperty("errors", List.of(new ApiError(null, "UNAUTHORIZED", messageKey, Map.of())));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
