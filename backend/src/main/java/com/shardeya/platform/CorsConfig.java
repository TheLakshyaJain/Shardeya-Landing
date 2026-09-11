package com.shardeya.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Nothing enforced this before M1 because every prior check hit the API
 * same-origin (curl, {@code TestRestTemplate}) or same-JVM (repository/service
 * tests) — never a real browser. The Vite dev server (5173) and the API
 * (8080) are different origins, so without this every request fails at the
 * browser's CORS preflight before it ever reaches a controller.
 *
 * <p><b>A genuine {@link CorsFilter} bean, deliberately NOT a
 * {@code WebMvcConfigurer#addCorsMappings} registration</b> — this codebase's
 * third occurrence of the same underlying CORS-header-gap class, and the one
 * that turned out to matter most. {@code WebMvcConfigurer}-based CORS is
 * only ever applied inside {@code DispatcherServlet}'s own dispatch, AFTER
 * every servlet {@code Filter} has already run — so a request
 * {@link TenantContextFilter} rejects outright (a missing, expired, or
 * otherwise invalid access token) writes its 401 directly from the filter
 * and NEVER reaches {@code DispatcherServlet} at all, meaning it never got a
 * CORS header under the old {@code addCorsMappings()}-only setup, no matter
 * how correctly that config was written. Confirmed directly while building
 * the httpOnly-refresh-cookie fix: a real browser blocked *reading* that
 * exact 401 ("No 'Access-Control-Allow-Origin' header is present"), which
 * {@code apiFetch} cannot distinguish from a network failure — its
 * refresh-on-401 retry logic never even runs, because {@code fetch()} itself
 * rejects before {@code res.status} is ever readable. This is almost
 * certainly the real mechanism behind "the session drops on its own after
 * ~15 minutes" (the very moment the access token naturally expired, the
 * browser-side silent-refresh path could never fire), not fundamentally the
 * refresh token's storage location — which is what the symptom looked like
 * from the outside. A 401 that DOES reach {@code DispatcherServlet} (e.g.
 * wrong-password login, thrown from {@code AuthService} and handled by
 * {@code GlobalExceptionHandler}) was confirmed to already carry the correct
 * headers even before this fix — isolating the gap precisely to
 * filter-rejected responses.
 *
 * <p>A plain servlet {@link CorsFilter}, unlike {@code WebMvcConfigurer},
 * runs for every request regardless of what happens downstream and adds its
 * response headers BEFORE any later filter/servlet gets a chance to write a
 * body — registered with {@link Ordered#HIGHEST_PRECEDENCE} so it always
 * runs before {@link TenantContextFilter} (which has no explicit
 * {@code @Order} and therefore defaults to lowest precedence) ever gets a
 * chance to reject anything. Its own {@code DefaultCorsProcessor} also fully
 * owns and terminates a real preflight (OPTIONS) request before the chain
 * continues — {@link TenantContextFilter}'s own OPTIONS bypass is kept as a
 * defensive backstop in case filter ordering is ever changed, not because
 * it's still load-bearing today.
 */
@Configuration
public class CorsConfig {

    @Value("${shardeya.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        // PUT added in M2 for grid-config and plot-position endpoints -- M1
        // never needed it, and a browser's CORS preflight silently blocks
        // any method missing from this list. Same class of "whitelist sized
        // for one milestone" gap as MagicBytes missing XLSX -- see
        // CLAUDE.md's M2 notes.
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        // Required for the httpOnly refresh-token cookie (see
        // AuthController) to ride along on cross-origin requests at all —
        // the browser drops Set-Cookie/Cookie on a credentialed request
        // whose CORS response doesn't echo this back. The spec forbids
        // combining this with a wildcard origin, which is exactly why
        // `allowedOrigins` above stays an explicit, configured list and
        // must never be relaxed to "*".
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
