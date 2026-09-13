I want to create a prototype to demonstrate resilience4j rate-limiter pattern.
Prototype is built around an order api that sends email notification when an order is received.

there are 2 main services: 'rest-service' and 'email-service'.
rest-service is a spring boot REST api.
email-service is a SpringApplication.
rest-service calls email-service when it receives an order.

add a rate limiter to the email-service, in the controller:
'''
@RateLimiter(name = RATE_LIMIT_NAME, fallbackMethod = "rateLimited")
...

Create both services in this repo, with their own pom.
make everything run in docker-compose. include a postgressql database.
include k6 tests to demonstrate it works.