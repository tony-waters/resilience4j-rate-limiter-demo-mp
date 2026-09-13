# Resilience4j Rate Limiter Demo

A prototype demonstrating the resilience4j rate-limiter pattern across a service boundary. See `CONTEXT.md` for the domain vocabulary and `docs/adr/` for the key design decisions.

## Services

- **`rest-service`** (port 8080): places Orders, persists them to Postgres, and calls `email-service` to notify the customer. Order creation always succeeds; the notification outcome (`SENT`/`RATE_LIMITED`/`FAILED`) is recorded on the order and returned in the response.
- **`email-service`** (port 8081): simulates sending the notification email. Its `/notifications` endpoint is annotated with resilience4j's `@RateLimiter`, configured to allow 5 requests per 10 seconds with no queuing — once that budget is spent, the fallback method returns `429` immediately.

## Running it

```
docker compose up --build
```

This starts Postgres, `email-service`, and `rest-service`, waiting on health checks so nothing races.

Try it manually:

```
curl -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"product":"Widget","quantity":1,"customerEmail":"buyer@example.com"}'

curl http://localhost:8080/orders
curl http://localhost:8080/orders/1
```

## Proving the rate limiter works

A k6 script (`k6/burst-test.js`) fires a burst of 20 `POST /orders` requests in quick succession and asserts every response is `201`, at least one notification was `SENT`, and at least one was `RATE_LIMITED`.

Run it inside docker-compose (no local k6 install needed):

```
docker compose --profile test run --rm k6
```

Or locally, against the stack already running via `docker compose up`:

```
BASE_URL=http://localhost:8080 k6 run k6/burst-test.js
```
