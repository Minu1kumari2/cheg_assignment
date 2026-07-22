# Rate Limiter — Case Study

A configurable, per-endpoint rate limiter built with Spring Boot, using a Token Bucket
algorithm and a custom Servlet Filter — no external rate-limiting libraries, no Redis,
in-memory state only.

## Setup & Run

```bash
mvn spring-boot:run
```

Runs on `http://localhost:8080`.

Run tests:

```bash
mvn test
```

**Note:** built on Spring Boot 3.3.4 (Java 17), matching the assignment's 3.x requirement.

## Endpoints

| Endpoint | Method | Limit |
|---|---|---|
| `/api/general` | GET | 20 req / 60 sec |
| `/api/submit` | POST | 5 req / 60 sec |
| `/api/status` | GET | 60 req / 60 sec |

`/api/status` returns the caller's current bucket state:
```json
{ "limit": 60, "remaining": 59, "resetAt": 1750000000 }
```

Every response includes `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and
`X-RateLimit-Reset`. Responses that exceed the limit return `429` with a
`Retry-After` header and body:
```json
{ "error": "Too many requests", "retryAfterSeconds": 42 }
```

## Client identification

Clients are identified by IP address (`request.getRemoteAddr()`), not API key.
This keeps the demo self-contained and easy to test with MockMvc (no header
juggling required). The tradeoff: IP-based identification can be bypassed by an
attacker rotating IPs, and shared IPs (e.g. behind a corporate NAT) would share
a single bucket. An API-key-based approach would need one extra check — reject
requests missing `X-API-Key` with `400` — the rest of the logic is unchanged.

## Architecture Thinking

### Interceptor vs Filter

I chose **`OncePerRequestFilter`** over `HandlerInterceptor`. The key difference:
a Filter runs at the Servlet layer, before Spring MVC resolves a handler for the
request. An Interceptor's `preHandle` runs later, after handler mapping has
already happened. For rate limiting, I want to reject abusive traffic as early
as possible in the request lifecycle — before spending any cycles on handler
resolution, argument binding, etc. Filters are also the more natural fit for
infrastructure-level, cross-cutting concerns like this, since they don't need
any knowledge of which controller method will eventually run. I'd reach for an
Interceptor instead if the logic needed access to handler metadata (e.g. a
custom annotation on the target method), which a Filter can't see.

### Algorithm: Token Bucket

I implemented **Token Bucket** over Fixed Window or Sliding Window Log. Each
client+endpoint pair gets a bucket that starts full (`limit` tokens) and
refills continuously at `limit / windowSizeInSeconds` tokens per second,
computed lazily on each access (no background thread needed). Each request
consumes one token; if none are available, the request is rejected.

**Why Token Bucket:** unlike Fixed Window, it doesn't have the boundary-burst
problem (e.g. a client sending `limit` requests at 0:59 and another `limit` at
1:01, doubling their effective rate around the window edge). It naturally
smooths traffic while still allowing short legitimate bursts, since unused
capacity accumulates up to `limit`.

**Its main weakness:** since clients are identified by IP, an attacker who can
rotate or spoof IP addresses gets a fresh bucket each time, fully bypassing the
limit. This is exploitable specifically in scenarios where clients sit behind
easily-changed or numerous IPs (e.g. botnets, or abuse via cheap proxy
rotation) — the rate limiter would need to fall back to a more stable
identifier (authenticated user ID, API key) to be robust against this.

### Distributed extension

This implementation keeps all bucket state in a single JVM's
`ConcurrentHashMap`, which works only for one instance. Across multiple pods
behind a load balancer, each pod would have its own independent, out-of-sync
view of every client's usage — effectively multiplying the real limit by the
number of pods.

To fix this, I'd move bucket state to **Redis**, since it's shared, fast, and
supports atomic operations across instances. The check-and-decrement (or
refill-and-consume) logic would need to run as a single atomic operation — a
Redis Lua script (via `EVAL`) is the standard way to do this, since it avoids
race conditions between the "read current tokens" and "write updated tokens"
steps that would otherwise happen as two separate network round-trips. Each
pod would call the same Lua script instead of holding its own local
`TokenBucket`; the in-memory map in this implementation would either go away
entirely or become, at most, a short-lived local cache in front of Redis.

## Memory cleanup strategy

Bucket entries are stored in a `ConcurrentHashMap<String, TokenBucket>` keyed
by `clientId:endpointPath`. To avoid unbounded growth from one-time or
abandoned clients, a `@Scheduled` task runs every 60 seconds and evicts any
bucket that hasn't been accessed in the last 10 minutes (tracked via each
bucket's `lastRefillTimestamp`). This is a scheduled approach rather than lazy
per-request cleanup, since it keeps the hot request path free of extra
iteration overhead — cleanup happens off to the side, on its own timer.

## AI-Assisted Development

**Tools used:** Claude (Anthropic), used conversationally throughout —
architecture discussion, code generation, and debugging Eclipse/Maven
environment issues.

**Where AI helped most:**
- Working through the Token Bucket refill math (lazy refill based on elapsed
  time, rather than a scheduled background thread)
- Structuring the multi-client, multi-endpoint key scheme in
  `RateLimiterService` (`clientId:endpointPath`)
- Debugging environment/dependency issues (a malformed `pom.xml` generated
  by an Eclipse wizard, referencing non-existent artifact IDs
  `spring-boot-starter-webmvc` / `spring-boot-starter-webmvc-test` instead of
  the correct `spring-boot-starter-web` / `spring-boot-starter-test`)

**What I manually corrected/implemented:**
- Verified and ran all code myself in Eclipse at every step, rather than
  accepting generated code blindly
- Manually tested boundary behavior (Nth/N+1th request, independent client
  counters, independent per-endpoint limits) via standalone `main()` methods
  before wiring anything into Spring, to isolate algorithm correctness from
  HTTP/Spring plumbing issues
- Fixed the `pom.xml` after identifying the incorrect artifact IDs were
  causing `ObjectMapper`/Jackson resolution failures at startup

**How I validated correctness:**
- Manual `main()`-based tests on `TokenBucket` and `RateLimiterService` in
  isolation, before any Spring wiring
- Manual Postman testing of all three endpoints, confirming headers,
  429 behavior, and independent per-endpoint/per-client limits
- Full JUnit 5 + MockMvc test suite (10/10 passing) covering all six
  required scenarios: within-limit requests, exact Nth/N+1th boundary,
  Retry-After presence, independent client counters, window reset behavior,
  and independent limits across endpoints
