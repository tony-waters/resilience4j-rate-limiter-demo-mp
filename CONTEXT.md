# Order Notification

A prototype demonstrating the resilience4j rate-limiter pattern: placing an Order triggers an attempt to notify the customer by email, and that notification path can be throttled independently of order placement.

## Language

**Order**:
A request to purchase, submitted through the ordering API. Placing an Order triggers an attempt to notify the customer by email, and the result of that attempt is recorded on the Order itself.
_Avoid_: Purchase, transaction

**Customer**:
The recipient of an Order's notification, identified for now by email address only.
_Avoid_: User, account

**Notification Outcome**:
The result of attempting to notify a Customer that their Order was placed: `Sent`, `RateLimited`, or `Failed`. Recorded on the Order and never affects whether the Order itself was placed.
_Avoid_: Email status, delivery status
