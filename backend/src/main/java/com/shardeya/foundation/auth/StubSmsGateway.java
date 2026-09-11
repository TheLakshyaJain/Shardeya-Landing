package com.shardeya.foundation.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Logs the OTP instead of sending a real SMS — CLAUDE.md: "build against a
 * stub SMS adapter... so the whole auth flow can be tested locally end to
 * end. We are not registering with a real SMS provider yet." Writes to both
 * SLF4J (visible via {@code docker compose logs -f backend}) and a plain file
 * (easy to {@code grep} from a test or a terminal without wading through
 * Spring Boot's log noise).
 */
@Component
@ConditionalOnProperty(prefix = "shardeya.sms", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(StubSmsGateway.class);

    private final Path logFile;

    public StubSmsGateway(@Value("${shardeya.sms.stub.log-file:/tmp/shardeya-otp-outbox.log}") String logFile) {
        this.logFile = Path.of(logFile);
    }

    @Override
    public void sendOtp(String mobile, String code, String purpose) {
        String line = "%s | purpose=%s | mobile=%s | code=%s".formatted(Instant.now(), purpose, mobile, code);
        log.info("[SMS STUB] {}", line);
        writeLine(line);
    }

    @Override
    public void sendText(String mobile, String text, String purpose) {
        String line = "%s | purpose=%s | mobile=%s | text=%s".formatted(Instant.now(), purpose, mobile, text);
        log.info("[SMS STUB] {}", line);
        writeLine(line);
    }

    private void writeLine(String line) {
        try {
            Files.writeString(logFile, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Could not write SMS stub log file at {}", logFile, e);
        }
    }
}
