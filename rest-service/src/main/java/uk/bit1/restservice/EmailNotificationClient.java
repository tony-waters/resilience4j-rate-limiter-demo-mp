package uk.bit1.restservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class EmailNotificationClient {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private static final String HEADER_REMAINING = "RateLimit-Remaining";
    private static final String HEADER_RESET = "RateLimit-Reset";

    private final RestClient restClient;

    // email-service's last-reported budget: null means "assume budget is available".
    // Single rest-service instance, in-memory only — see ADR-0004.
    private final AtomicReference<Instant> blockedUntil = new AtomicReference<>();

    public EmailNotificationClient(@Value("${email-service.base-url}") String emailServiceBaseUrl) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT);
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);
        this.restClient = RestClient.builder()
                .baseUrl(emailServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public NotificationOutcome notify(Order order) {
        Instant blockedUntilValue = blockedUntil.get();
        if (blockedUntilValue != null && Instant.now().isBefore(blockedUntilValue)) {
            log.info("Skipping email-service call for order {}: known rate-limited until {}",
                    order.getId(), blockedUntilValue);
            return NotificationOutcome.SKIPPED;
        }

        NotificationRequest request = new NotificationRequest(
                order.getId(), order.getCustomerEmail(), order.getProduct(), order.getQuantity());
        try {
            return restClient.post()
                    .uri("/notifications")
                    .body(request)
                    .exchange((req, res) -> {
                        recordRateLimitState(res.getHeaders());
                        HttpStatusCode status = res.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            return NotificationOutcome.SENT;
                        }
                        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                            return NotificationOutcome.RATE_LIMITED;
                        }
                        return NotificationOutcome.FAILED;
                    });
        } catch (Exception e) {
            log.warn("Failed to reach email-service for order {}: {}", order.getId(), e.getMessage());
            return NotificationOutcome.FAILED;
        }
    }

    private void recordRateLimitState(HttpHeaders headers) {
        String remainingHeader = headers.getFirst(HEADER_REMAINING);
        String resetHeader = headers.getFirst(HEADER_RESET);
        if (remainingHeader == null || resetHeader == null) {
            return;
        }
        try {
            int remaining = Integer.parseInt(remainingHeader);
            long resetSeconds = Long.parseLong(resetHeader);
            blockedUntil.set(remaining <= 0 ? Instant.now().plusSeconds(resetSeconds) : null);
        } catch (NumberFormatException e) {
            log.warn("Malformed rate-limit headers from email-service: {}={}, {}={}",
                    HEADER_REMAINING, remainingHeader, HEADER_RESET, resetHeader);
        }
    }

    private record NotificationRequest(Long orderId, String recipientEmail, String product, int quantity) {
    }
}
