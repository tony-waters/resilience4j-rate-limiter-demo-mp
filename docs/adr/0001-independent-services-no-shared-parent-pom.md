# Independent services, no shared parent pom

`rest-service` and `email-service` are separate Spring Boot applications demonstrating resilience4j rate limiting across a service boundary. Each has its own `pom.xml` with its own Spring Boot parent and dependency versions; there is no aggregator/reactor pom tying them together. This keeps each service copy-pasteable as a standalone example, at the cost of managing dependency versions separately in each pom rather than in one place.
