package uk.bit1.restservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class EmailNotificationClient {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

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
        NotificationRequest request = new NotificationRequest(
                order.getId(), order.getCustomerEmail(), order.getProduct(), order.getQuantity());
        try {
            return restClient.post()
                    .uri("/notifications")
                    .body(request)
                    .exchange((req, res) -> {
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

    private record NotificationRequest(Long orderId, String recipientEmail, String product, int quantity) {
    }
}
