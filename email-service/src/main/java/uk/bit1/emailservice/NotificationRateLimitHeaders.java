package uk.bit1.emailservice;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
class NotificationRateLimitHeaders {

    // resilience4j doesn't expose the RateLimiter's internal refresh-cycle boundary,
    // so we track an equivalent cycle independently, phase-aligned to this bean's
    // construction (which happens alongside the RateLimiter's own, at app startup).
    private final long cycleStartNanos = System.nanoTime();
    private final RateLimiter rateLimiter;

    NotificationRateLimitHeaders(RateLimiterRegistry registry) {
        this.rateLimiter = registry.rateLimiter(NotificationController.RATE_LIMIT_NAME);
    }

    HttpHeaders asHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("RateLimit-Limit", String.valueOf(limit()));
        headers.add("RateLimit-Remaining", String.valueOf(remaining()));
        headers.add("RateLimit-Reset", String.valueOf(resetSeconds()));
        return headers;
    }

    long resetSeconds() {
        long refreshPeriodNanos = rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod().toNanos();
        long elapsedNanos = System.nanoTime() - cycleStartNanos;
        long currentCycle = elapsedNanos / refreshPeriodNanos;
        long nanosToNextCycle = (currentCycle + 1) * refreshPeriodNanos - elapsedNanos;
        return (nanosToNextCycle + 999_999_999L) / 1_000_000_000L;
    }

    private int limit() {
        return rateLimiter.getRateLimiterConfig().getLimitForPeriod();
    }

    private int remaining() {
        return Math.max(0, rateLimiter.getMetrics().getAvailablePermissions());
    }
}
