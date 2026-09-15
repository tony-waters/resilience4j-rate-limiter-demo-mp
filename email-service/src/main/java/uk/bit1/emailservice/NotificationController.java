package uk.bit1.emailservice;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    static final String RATE_LIMIT_NAME = "emailNotification";
    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationRateLimitHeaders rateLimitHeaders;

    public NotificationController(NotificationRateLimitHeaders rateLimitHeaders) {
        this.rateLimitHeaders = rateLimitHeaders;
    }

    @PostMapping("/notifications")
    @RateLimiter(name = RATE_LIMIT_NAME, fallbackMethod = "rateLimited")
    public ResponseEntity<NotificationResponse> sendNotification(@RequestBody NotificationRequest request) {
        log.info("Sending email to {} for order {}", request.recipientEmail(), request.orderId());
        return ResponseEntity.ok()
                .headers(rateLimitHeaders.asHeaders())
                .body(new NotificationResponse("SENT", "Email sent"));
    }

    public ResponseEntity<NotificationResponse> rateLimited(NotificationRequest request, Throwable throwable) {
        log.info("Rate limiting on request from {} for order {}", request.recipientEmail(), request.orderId());
        HttpHeaders headers = rateLimitHeaders.asHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, headers.getFirst("RateLimit-Reset"));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(new NotificationResponse("RATE_LIMITED", "Too many notification requests"));
    }
}
