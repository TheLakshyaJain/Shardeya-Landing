package com.shardeya.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for backend integration tests. Mirrors production's role split exactly:
 * Flyway migrates as the container's bootstrap superuser (same role shape as
 * docker-compose's POSTGRES_USER), then the app's own datasource connects as
 * {@code shardeya_app} — the non-superuser runtime role V0_005 creates — so
 * these tests exercise the real RLS path, not a superuser bypass of it.
 *
 * <p>Deliberately NOT {@code @Testcontainers}/{@code @Container} — those
 * annotations stop the container after each test <i>class</i> that uses it,
 * but a static field declared in this base class is the same field shared by
 * every subclass. First subclass to finish stops it out from under every
 * subsequent one ("Connection refused" with no obvious link to the real
 * cause). This is Testcontainers' own documented "singleton container"
 * pattern instead: start once in a static initializer, never stop — the JVM
 * shutdown hook Testcontainers registers on {@code start()} cleans up when
 * the test run ends.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // The same MailHog image local dev already runs via docker-compose --
    // added because the Email OTP fix made this genuinely necessary, not as
    // an unrelated improvement: OtpService's email channel calls
    // EmailGateway.send() synchronously (matching SmsGateway.sendOtp()'s
    // own always-been-synchronous shape -- there's no tenant/org context at
    // signup time to scope an outbox event to, so routing through the
    // outbox was never architecturally available for this path either).
    // Before this, nothing in the test suite gave spring.mail.host:port
    // anywhere real to connect to -- any test triggering an email send
    // (the existing welcome-email-on-signup, forgot-password's reset email)
    // only ever "worked" because those go through the async outbox, whose
    // poller catches and merely logs a send failure rather than failing the
    // request/test. A synchronous send has no such safety net: without a
    // real SMTP server here, every signup/login-OTP request would 500 in
    // CI, which has no docker-compose MailHog and no other mail server
    // reachable at localhost:1025 at all.
    static final GenericContainer<?> MAILHOG = new GenericContainer<>(DockerImageName.parse("mailhog/mailhog:v1.0.1"))
            .withExposedPorts(1025, 8025);

    static {
        POSTGRES.start();
        REDIS.start();
        MAILHOG.start();
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "shardeya_app");
        registry.add("spring.datasource.password", () -> "shardeya_app");

        registry.add("shardeya-auth-lookup-datasource.url", POSTGRES::getJdbcUrl);
        registry.add("shardeya-auth-lookup-datasource.username", () -> "shardeya_authlookup");
        registry.add("shardeya-auth-lookup-datasource.password", () -> "shardeya_authlookup");

        registry.add("shardeya-stats-refresh-datasource.url", POSTGRES::getJdbcUrl);
        registry.add("shardeya-stats-refresh-datasource.username", () -> "shardeya_statsrefresh");
        registry.add("shardeya-stats-refresh-datasource.password", () -> "shardeya_statsrefresh");

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.mail.host", MAILHOG::getHost);
        registry.add("spring.mail.port", () -> MAILHOG.getMappedPort(1025));
    }

    /** MailHog's own REST API (v2), for tests that need to read back a real sent email rather than trust the send call alone. */
    protected static String mailhogApiBaseUrl() {
        return "http://" + MAILHOG.getHost() + ":" + MAILHOG.getMappedPort(8025);
    }
}
