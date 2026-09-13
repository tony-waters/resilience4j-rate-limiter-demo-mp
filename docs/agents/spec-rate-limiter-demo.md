## Problem Statement

There's no runnable, observable demonstration of the resilience4j rate-limiter pattern in this repo. Understanding how a rate limiter behaves under load — what a caller sees when the limit is exceeded, how a downstream fallback engages, how an upstream service should react — currently requires reading resilience4j's own docs rather than seeing the pattern applied to a realistic (if small) service boundary.

## Solution

Build a small two-service prototype — an order-placement API (`rest-service`) that calls a notification service (`email-service`) — where `email-service` enforces a resilience4j rate limiter on its notification endpoint. Run the whole stack in docker-compose with a real Postgres database, and prove the rate limiter engages (and that the caller degrades gracefully) with an automated k6 load test that bursts past the configured limit.

## User Stories

1. As a developer exploring resilience4j, I want to place an order via a REST call, so that I can trigger the end-to-end flow that exercises the rate limiter.
2. As a developer exploring resilience4j, I want `rest-service` to persist the order regardless of what happens downstream, so that I can see order placement is decoupled from notification delivery.
3. As a developer exploring resilience4j, I want each order's response to tell me whether its notification was sent, rate-limited, or failed, so that I don't have to cross-reference logs to see the outcome.
4. As a developer exploring resilience4j, I want to fetch a previously placed order by id, so that I can inspect its recorded notification outcome after the fact.
5. As a developer exploring resilience4j, I want to list all orders, so that I can see the distribution of outcomes across a batch of requests.
6. As a developer exploring resilience4j, I want `email-service`'s notification endpoint annotated with `@RateLimiter(name = RATE_LIMIT_NAME, fallbackMethod = "rateLimited")`, so that the demo uses the exact annotation-driven style resilience4j is known for.
7. As a developer exploring resilience4j, I want the rate limiter configured with a small, fast-refreshing budget (5 requests per 10 seconds, fail-fast with no queuing), so that a short-lived k6 run can trigger it without waiting a long time.
8. As a developer exploring resilience4j, I want `email-service`'s fallback method to return a clear 429 response with a machine-readable status, so that `rest-service` (and any other caller) can distinguish "rate limited" from "the service is broken."
9. As a developer exploring resilience4j, I want `email-service` to simulate sending an email (log it) rather than integrate real SMTP, so that the demo has no external dependency unrelated to the pattern being shown.
10. As a developer running this demo, I want to bring up the entire stack with a single `docker compose up --build`, so that I don't need local Maven, Java, or Postgres installed to see it work.
11. As a developer running this demo, I want compose to wait for Postgres and `email-service` to be actually ready (not just container-started) before starting `rest-service`, so that the stack doesn't race and flake on startup.
12. As a developer running this demo, I want a k6 script that bursts requests at `rest-service` and asserts both a `SENT` and a `RATE_LIMITED` outcome appear in the results, so that a single test run is itself proof the pattern works, not just a smoke test.
13. As a developer running this demo, I want to run the k6 test either inside docker-compose (`docker compose --profile test run k6`) or locally against the exposed ports, so that I have flexibility depending on whether I have the k6 binary installed.
14. As a developer reading this repo, I want the domain vocabulary (Order, Customer, Notification Outcome) and the key architectural decisions recorded in `CONTEXT.md`/`docs/adr/`, so that the "why" behind the design isn't lost to the git history alone.

## Implementation Decisions

- **Toolchain**: Java 21 (LTS), Spring Boot 3.3.x, resilience4j-spring-boot3 2.2.x, Maven.
- **Module layout**: `rest-service/` and `email-service/` at the repo root, each a fully independent Maven project — own `pom.xml`, own Spring Boot parent, own dependency versions, no aggregator/reactor pom (see ADR 0001). Base packages `uk.bit1.restservice` and `uk.bit1.emailservice`.
- **`rest-service`**:
  - Persists `Order` to Postgres via Spring Data JPA: id, product, quantity, customerEmail, `notificationOutcome` (enum: `SENT`/`RATE_LIMITED`/`FAILED`, persisted), created_at.
  - Endpoints: `POST /orders`, `GET /orders/{id}`, `GET /orders`.
  - On create: saves the order, synchronously calls `email-service`'s notification endpoint, maps the outcome (2xx → `SENT`; 429 with the rate-limit fallback body → `RATE_LIMITED`; anything else, including exceptions/timeouts/5xx → `FAILED`), persists that outcome, and always returns `201 Created` with the order (including `notificationOutcome`) in the body. Order creation never fails because of the downstream notification result (see ADR 0002).
  - Port 8080.
- **`email-service`**:
  - Stateless — no database.
  - Notification controller method annotated `@RateLimiter(name = RATE_LIMIT_NAME, fallbackMethod = "rateLimited")`, where `RATE_LIMIT_NAME = "emailNotification"` is a constant on the controller, and `resilience4j.ratelimiter.instances.emailNotification` in `application.yml` configures `limitForPeriod=5`, `limitRefreshPeriod=10s`, `timeoutDuration=0` (fail fast, no queuing for a free slot).
  - On an allowed call: logs `Sending email to {address} for order {orderId}` and returns `200` — no real SMTP integration, purely simulated.
  - `rateLimited` fallback method returns `429 Too Many Requests` with body `{"status":"RATE_LIMITED","message":"Too many notification requests"}`.
  - Port 8081.
- **Infra**: docker-compose defines `postgres` (db/user/pass `orders`), `email-service`, `rest-service`, and a profile-gated `k6` service (image `grafana/k6`). Each Spring service has a multi-stage Dockerfile (Maven build stage → slim JRE runtime image) and exposes Spring Boot Actuator's `/actuator/health` for a compose healthcheck; `depends_on` uses `condition: service_healthy` throughout — `rest-service` waits on `postgres` and `email-service`; `k6` waits on `rest-service`.
- **Domain docs**: `CONTEXT.md` defines `Order`, `Customer`, and `Notification Outcome` as the project's ubiquitous language. `docs/adr/0001`–`0003` record: independent services with no shared parent pom; order creation being resilient to (decoupled from) notification outcome; and the k6-only testing strategy. These should be kept in sync if implementation deviates from what's recorded.

## Testing Decisions

- **Single seam, by design**: the only test layer is an end-to-end k6 script driving `rest-service`'s public HTTP API (`POST /orders`, and optionally `GET /orders`/`GET /orders/{id}` for inspection) against the full docker-compose stack — real Postgres, real `email-service`, real rate limiter. No unit tests, no component/slice tests, no mocks or test doubles anywhere (ADR 0003). This is a deliberate scope decision for a prototype whose entire purpose is to demonstrate the pattern working, not to harden a production service.
- **What a good test looks like here**: assert on external behavior only — HTTP status codes and response body fields (`notificationOutcome`) — never on internal implementation (e.g., don't assert log lines, don't reach into the JVM, don't inspect resilience4j internals directly).
- **The k6 script** (`k6/` at repo root): fires a tight burst of 20 `POST /orders` requests in quick succession (no ramp-up — a burst is what actually exercises a rate limiter; a ramp would let the limiter's refresh window keep pace). Checks assert: every response is `201`; at least one response has `notificationOutcome: "SENT"`; at least one has `notificationOutcome: "RATE_LIMITED"`. Failing either of the latter two checks means the run didn't actually prove the pattern, which is the real point of the test — not merely "did the API respond."
- **No prior art in this repo** for k6 or any test type — this is a fresh repo, so there's nothing existing to follow conventions from.
- **Runnable two ways**: `docker compose --profile test run k6` (no local dependencies beyond Docker), or directly with a local `k6` binary against the exposed host ports, documented in the README.

## Out of Scope

- Real email delivery (SMTP, any mail provider integration).
- Any unit or integration test layer beyond the k6 script (explicitly deferred per ADR 0003; first thing to add if this grows past a prototype).
- Authentication/authorization on either service's API.
- A circuit breaker, retry, or bulkhead — this prototype demonstrates the rate-limiter pattern only, not the full resilience4j suite.
- A shared/aggregator Maven pom or monorepo build tooling across the two services (ADR 0001).
- Order lifecycle beyond creation and read (no update, cancel, or status transitions beyond the notification outcome).
- CI/CD pipeline wiring for this repo.
- Production-grade Postgres credentials/secrets management — the demo hardcodes local-only credentials in docker-compose.

## Further Notes

- This spec was produced via a full grilling + domain-modeling session (see `CONTEXT.md` and `docs/adr/0001`–`0003`); all values above (ports, limiter tuning, status names, etc.) were explicit decisions made in that session, not defaults picked unilaterally during implementation.
- `docs/agents/issue-tracker.md` designates GitHub Issues as this repo's tracker, but the `gh` CLI isn't available in the environment this spec was authored in, so this spec was written to `docs/agents/spec-rate-limiter-demo.md` instead of being published directly. Whoever picks this up should create the GitHub issue from this file's contents and apply the `ready-for-agent` label, then this file can be deleted.
