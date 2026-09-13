# k6 end-to-end tests only, for this prototype

This repo exists to demonstrate the resilience4j rate-limiter pattern, not to ship a production service. The only test layer is a k6 script exercising the full stack through `rest-service`; there are no unit or slice tests for either service. Unit tests would re-exercise the same resilience4j configuration in isolation without adding to what the demo needs to prove. This is a deliberate scope choice, not an oversight — the first thing to add back if this grows past a prototype.
