package com.shardeya.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the OTP code the StubSmsGateway wrote to its plain-text log for a given mobile — the actual, real value, not a guess. */
public final class OtpStubReader {

    private static final Path LOG_FILE = Path.of("/tmp/shardeya-otp-outbox.log");

    private OtpStubReader() {
    }

    public static String lastCodeFor(String mobile) throws IOException {
        String content = Files.readString(LOG_FILE);
        Matcher matcher = Pattern.compile("mobile=" + mobile + " \\| code=(\\d{6})").matcher(content);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        if (last == null) {
            throw new IllegalStateException("No OTP code logged for mobile " + mobile);
        }
        return last;
    }
}
