package com.shardeya.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a real, actually-delivered OTP email back from MailHog's own REST
 * API (v2) — the actual value, not a guess. The email-channel counterpart to
 * {@link OtpStubReader} (which reads the SMS stub's log file); pairs with
 * {@link AbstractIntegrationTest}'s Testcontainers-managed MailHog
 * container, added specifically because the Email OTP fix needed a real
 * SMTP server reachable in tests, not just in local dev's docker-compose.
 */
public final class MailHogReader {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern CODE_PATTERN = Pattern.compile("code is: (\\d{6})");

    private MailHogReader() {
    }

    public static String lastOtpCodeFor(String mailhogApiBaseUrl, String email) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mailhogApiBaseUrl + "/api/v2/messages?limit=50"))
                    .GET().build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode items = OBJECT_MAPPER.readTree(response.body()).path("items");
            for (JsonNode item : items) {
                for (JsonNode addr : item.at("/Content/Headers/To")) {
                    if (addr.asText().toLowerCase().contains(email.toLowerCase())) {
                        String body = item.at("/Content/Body").asText();
                        Matcher matcher = CODE_PATTERN.matcher(body);
                        if (matcher.find()) {
                            return matcher.group(1);
                        }
                    }
                }
            }
            throw new IllegalStateException("No OTP email found for " + email);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read MailHog messages for " + email, e);
        }
    }
}
