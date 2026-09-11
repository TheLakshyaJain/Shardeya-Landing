package com.shardeya.platform;

import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real bugs found live (see PATTERNS.md / CLAUDE.md): "/api/v1/docs" was
 * allowlisted as public, but that path only covers springdoc's initial
 * redirect -- the UI it redirects to actually lives under the fixed
 * "/api/v1/swagger-ui/**" path, which was NOT allowlisted, so following the
 * redirect 401'd before the page could render.
 *
 * <p>Second bug, found while building the httpOnly-refresh-cookie fix: a
 * 401 this filter writes DIRECTLY (an invalid/expired/missing access token)
 * bypasses DispatcherServlet entirely, so the old {@code WebMvcConfigurer}
 * -based CORS registration never got a chance to add its headers -- a real
 * browser blocked reading that exact 401, indistinguishable from a network
 * failure to the frontend's own refresh-on-401 logic. Fixed by replacing
 * {@code CorsConfig}'s registration with a genuine {@code CorsFilter} bean
 * that runs ahead of this filter (see that class's own javadoc).
 */
class TenantContextFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void swaggerUiRedirectTargetIsReachableWithoutAToken() {
        ResponseEntity<String> redirect = restTemplate.getForEntity("http://localhost:" + port + "/api/v1/docs", String.class);
        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String location = redirect.getHeaders().getLocation().toString();

        ResponseEntity<String> uiPage = restTemplate.getForEntity("http://localhost:" + port + location, String.class);
        assertThat(uiPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uiPage.getBody()).contains("swagger-ui");
    }

    @Test
    void openApiJsonSpecIsReachableWithoutAToken() {
        ResponseEntity<String> spec = restTemplate.getForEntity("http://localhost:" + port + "/api/v1/openapi", String.class);
        assertThat(spec.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(spec.getBody()).contains("\"openapi\"");
    }

    // The exact scenario a real browser hits every time an access token
    // naturally expires (or is otherwise invalid): this filter rejects the
    // request and writes the 401 itself, before DispatcherServlet ever runs.
    // Without a real CorsFilter running ahead of this filter, that response
    // has no Access-Control-Allow-Origin header at all -- a browser blocks
    // reading it outright, which is indistinguishable from a network error
    // to apiFetch's refresh-on-401 logic, silently breaking the entire
    // silent-refresh mechanism for every real cross-origin deployment of
    // this app.
    @Test
    void aFilterRejectedUnauthorizedResponseStillCarriesCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "http://localhost:5173");
        headers.setBearerAuth("this-is-not-a-valid-jwt");

        ResponseEntity<String> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
        assertThat(resp.getHeaders().getFirst("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    // Same rejection path, but for a request with NO Authorization header at
    // all (the missing-token branch, not the invalid-token branch) -- a
    // different code path inside the filter (writeUnauthorized called
    // directly from doFilterInternal, not from bindTenantFromToken's catch),
    // worth covering separately since it's genuinely different code.
    @Test
    void aFilterRejectedMissingTokenResponseStillCarriesCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "http://localhost:5173");

        ResponseEntity<String> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
    }
}
