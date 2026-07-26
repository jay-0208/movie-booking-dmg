# Movie Ticket Booking System

SDE-2 take-home submission. A seat-level movie ticket booking backend
supporting multiple cities/theaters/screens/shows, concurrency-safe seat
holds, tiered pricing, discount codes, payment (simulated), cancellation with
configurable refund policies, and admin + customer RBAC.

## Tech Stack
- **Java 17 / Spring Boot 3.3** — team-standard, mature ecosystem, and Spring
  Data JPA's built-in support for pessimistic locking (`@Lock(PESSIMISTIC_WRITE)`)
  is a direct, low-ceremony fit for this assignment's core concurrency
  requirement — no need to reach for an external lock manager for a
  single-database system.
- **Spring Data JPA + H2 (dev) / PostgreSQL (prod-ready swap)** — H2 for
  zero-setup local runs and tests; the `postgresql` driver is already on the
  classpath so switching `spring.datasource.url` is the only change needed
  for a real deployment.
- **Spring Security (HTTP Basic + RBAC)** — the assignment explicitly scopes
  out OAuth/SSO/MFA and only asks for basic RBAC, so HTTP Basic backed by a
  real `UserDetailsService` (not Spring's auto-generated fallback user) is
  the correctly-sized solution, not a placeholder for something bigger.
- **Spring `@Async` + `@Scheduled`** — for non-blocking confirmation
  notifications and the seat-hold expiry sweep, respectively. No message
  broker — out of scope for this system's size, and adds an operational
  dependency the assignment doesn't ask for.

## Architecture Overview
The catalog model is straightforward: `City → Theater → Screen → Seat`,
`Movie`, and `Show` (a movie playing on a screen at a given time). The
one non-obvious modeling decision — and the most important one in the whole
system — is **`ShowSeat`**: a separate table with one row per `(show, seat)`
pair, holding availability status (`AVAILABLE` / `HELD` / `BOOKED`) and hold
expiry. `Seat` itself is pure layout metadata (which row, which tier) and
never carries a booking status.

This split exists because seat availability is inherently **per-show, not
per-seat**: physical seat A12 is available for the 6pm showing and
simultaneously booked for the 9pm showing of a different movie on the same
screen. Modeling status directly on `Seat` would make that impossible to
represent correctly. `ShowSeat` is also the row that gets locked
(`SELECT ... FOR UPDATE`) during a booking attempt — see Concurrency
Handling below — so keeping it as its own table with its own primary key
gives the lock a precise, minimal target instead of locking a whole `Seat`
row that's shared across every show that seat has ever been part of.

`Booking` ties a user, a show, and a set of `ShowSeat` rows together with a
status (`PENDING_PAYMENT → CONFIRMED / CANCELLED / EXPIRED`) and a total.
`Payment` is a simulated one-to-one record against a booking (see
Assumptions). `DiscountCode` and `RefundPolicy` are admin-managed,
independent of the booking flow itself — `PricingService` and
`CancellationService` read them but don't own their lifecycle.

## Concurrency Handling
Two or more users hitting `holdSeats()` for the same seat on the same show
at the same instant is the scenario this assignment is really testing.

**Approach taken: pessimistic row locking.** `ShowSeatRepository` exposes a
`@Lock(LockModeType.PESSIMISTIC_WRITE)` query that issues a
`SELECT ... FOR UPDATE` on exactly the `ShowSeat` rows being requested, inside
the same `@Transactional` boundary as the rest of the hold logic. A second
concurrent request for any of the same seats blocks at that query — it can't
read a stale `AVAILABLE` and race ahead — until the first transaction commits
or rolls back, at which point it sees the real, current status and correctly
fails with `SeatUnavailableException` if the seat's gone.
`BookingServiceConcurrencyTest` proves this directly: 10 threads fire at the
same seat simultaneously, and the test asserts exactly one succeeds and nine
get a clean `SeatUnavailableException`, not a corrupted double-booking.

The same pattern is reused for `DiscountCode` redemption counts
(`findByCodeAndActiveTrueForUpdate`), since a limited-redemption code has the
identical race condition on a smaller scale.

**Tradeoff, stated explicitly:** pessimistic locking is correct and simple to
reason about, but it means concurrent requests for a *popular* show queue up
on the database rather than failing fast or being handled optimistically.
The alternative — optimistic locking via `@Version` (already present on
`ShowSeat` as a second line of defense) with retry-on-conflict — gives better
throughput under low contention but a worse user experience under high
contention (e.g., a blockbuster's seats opening at midnight, where many
users legitimately collide on adjacent seats). At the scale a single Postgres
instance can serve, pessimistic locking is the right choice; if this needed
to scale past one database, the seat lock would need to move to something
like a Redis-based distributed lock keyed per seat, with the database as the
source of truth behind it — genuinely out of scope here per the assignment's
own "no distributed systems" carve-out.

## Assumptions
Every place the spec was silent and a decision had to be made, listed here
rather than buried in code comments alone (though the reasoning is also
inline at each site):

- **Seat availability is scoped per show, not per screen** — see
  Architecture Overview above. This is the foundational assumption everything
  else is built on.
- **Seat hold expiry defaults to 5 minutes**, configurable via
  `app.seat-hold.expiry-minutes`. The sweep job that releases expired holds
  runs every 30 seconds (`SeatHoldExpiryJob`), not instantly on expiry — a
  polling interval, not a per-hold timer, which is the right tradeoff for a
  take-home but wouldn't be at real scale (a delayed-job/TTL mechanism would
  be preferable there).
- **Weekend and premium pricing multipliers stack multiplicatively** — a
  premium seat on a weekend show gets both (e.g. 200 × 1.8 × 1.5 = 540), not
  just the larger of the two. This was a genuine judgment call; taking the
  max instead is an equally defensible alternative and a one-line change in
  `PricingService`.
- **Only one discount code per booking**, no stacking.
- **A discount code's redemption count increments at hold time, not payment
  confirmation.** An expired, never-paid hold still consumes one redemption
  and it is not returned. Simpler and correct enough for this scope; the more
  precise alternative (increment on confirm, decrement on expiry/cancel) adds
  meaningfully more state to track for comparatively little payoff here.
- **Refund percentage resolution: highest matching hours-before-showtime tier
  wins** (e.g. tiers at 24h/2h/0h — cancelling 30h out gets the 24h tier's
  rate). **If no active `RefundPolicy` exists at all, the system defaults to
  a 0% refund, not 100%** — a deliberate fail-closed choice so a
  misconfigured system under-refunds rather than over-refunds. This is backed
  by a test (`CancellationServiceTest.noActiveRefundPolicyConfigured...`).
- **Only `CONFIRMED` bookings can be cancelled through `CancellationService`.**
  A `PENDING_PAYMENT` booking isn't "cancelled" in the refund sense — there's
  no payment yet — it's simply abandoned and left for the expiry sweep job,
  which is the single place a hold gets released, rather than having two
  different code paths that can both end a hold.
- **A booking that exists but belongs to another user returns 404, not
  403**, on lookup — this avoids confirming a booking ID's existence to
  someone who shouldn't see it either way.
- **Zero-refund cancellations still use `PaymentStatus.PARTIALLY_REFUNDED`**
  (with `refundedAmount = 0`) rather than a dedicated "no refund" status,
  since the enum doesn't model that state separately. A minor, acknowledged
  overload of that value rather than adding a fifth enum constant for one
  edge case.
- **Payment is fully simulated** — no real PSP integration (explicitly out
  of scope). It "fails" only above a hardcoded test threshold so the failure
  path is exercisable; this is clearly not realistic payment logic and isn't
  meant to be.
- **Creating a Show bulk-creates one `ShowSeat` per physical seat on that
  screen immediately.** If the screen has no seat layout defined yet, the
  show is still created — just with zero bookable seats — rather than a hard
  rejection. The alternative (400 if no layout exists) is equally
  defensible and a small change if you'd rather enforce it.
- **`DiscountCode` and `RefundPolicy` deletions are soft (deactivate), not
  hard deletes** — hard-deleting either would orphan `Booking.discountCode`
  references and break `CancellationService`'s lookup of a policy a past
  cancellation relied on.
- **Self-registration (`POST /api/auth/register`) only ever creates
  `CUSTOMER` users.** There is no public way to self-register as `ADMIN` —
  that would be a straightforward privilege-escalation hole. A single default
  admin is instead seeded on startup by `DevDataSeeder` — explicitly a
  dev/demo convenience, not a production pattern (see Security Notes below).
- **`spring.jpa.open-in-view` is left at its Spring Boot default (`true`)**,
  which is what allows `BookingMapper` to lazily touch
  `booking.getShow().getMovie()` etc. outside the originating
  `@Transactional` method. Many teams consider this bad practice for
  production (hidden N+1 queries, DB connections held longer than
  necessary) — left on here for simplicity, called out rather than silently
  relied on.

## API Overview

**Public / customer** (`/api/**`)
| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register` | Creates a CUSTOMER user |
| GET | `/api/shows/{showId}/seats` | Seat availability map, public |
| POST | `/api/bookings` | Hold seats |
| POST | `/api/bookings/{id}/confirm` | Simulated payment + confirm |
| DELETE | `/api/bookings/{id}` | Cancel a CONFIRMED booking, triggers refund |
| GET | `/api/bookings/{id}` | Single booking (owner only) |
| GET | `/api/bookings` | Current user's booking history |

**Admin** (`/api/admin/**`, requires `ROLE_ADMIN`)
| Resource | Operations |
|---|---|
| `/cities` | full CRUD |
| `/theaters` | full CRUD, filterable by `cityId` |
| `/movies` | full CRUD |
| `/screens` | create/get/delete + `POST /{id}/seat-layout` (bulk seat creation) + `GET /{id}/seats` |
| `/shows` | create (bulk-creates `ShowSeat` rows)/list/get/delete |
| `/discount-codes` | full CRUD (delete = deactivate) |
| `/refund-policies` | full CRUD (delete = deactivate) |

All error responses use a consistent shape:
`{ timestamp, status, error, message, fieldErrors[] }`.

## AI Workflow Used
See `CLAUDE.md` for the full account (required submission) and
`STEPS_AND_PROMPTS.md` for the exact prompt sequence used at each stage.

## Testing Approach
- **`BookingServiceConcurrencyTest`** (integration, real H2 + real
  transactions) — the assignment's central requirement: 10 threads racing for
  one seat, asserting exactly one hold succeeds.
- **`PricingServiceTest`** (unit, mocked repo) — the full pricing/discount
  matrix: weekday/weekend × regular/premium, percentage vs. flat discounts,
  expired codes, redemption limits, invalid codes.
- **`CancellationServiceTest`** (unit, mocked deps) — all three refund tiers,
  the fail-closed "no policy configured" case, rejecting cancellation of
  non-`CONFIRMED` bookings, and seat release on cancellation.
- **`BookingLifecycleIntegrationTest`** (integration, real H2) — four
  end-to-end round trips: happy path (hold → confirm → visible in history),
  expiry path (hold → backdate → sweep job runs → seat is bookable by a
  *second* user, proving the release actually works), cancel path (hold →
  confirm → cancel → seat released), and rejecting a double-confirm.

**Not covered**, given the 48h scope: controller-layer tests (MockMvc) for
request validation/HTTP status mapping, admin CRUD endpoint tests, and load
testing beyond the 10-thread concurrency test. Called out here rather than
left silent.

## Running Locally
```
mvn spring-boot:run
```
On first startup, `DevDataSeeder` creates a default admin user and logs the
credentials to the console (see Security Notes — dev-only, not a production
pattern). H2 console at `/h2-console` (JDBC URL `jdbc:h2:mem:moviebooking`).

Example admin call:
```
curl -u admin@moviebooking.local:ChangeMe123! http://localhost:8080/api/admin/cities
```

## Security Notes
Reviewed deliberately rather than left implicit, since "basic RBAC" and
"input validation and error handling" are explicit in-scope requirements:

- **Passwords** are BCrypt-hashed (`PasswordEncoder` bean), never logged or
  returned in any response (`UserResponse` excludes the hash entirely).
- **RBAC is actually wired**, not just declared — `CustomUserDetailsService`
  maps `User.role` to a Spring Security authority; without it,
  `hasRole("ADMIN")` in `SecurityConfig` would never pass for anyone
  (Spring Boot's fallback is a single auto-generated user with no roles).
  This was a real gap caught and fixed during development, not a
  hypothetical — see `CLAUDE.md`.
- **IDOR protection**: booking lookups check ownership and return 404 (not
  403) for another user's booking, avoiding existence-leak.
- **No SQL injection surface**: all queries are JPQL with named parameters
  via Spring Data — no native/raw SQL anywhere in the codebase.
- **CSRF is disabled** — correct for a stateless, token/Basic-auth REST API
  with no browser session/cookie state to forge, not an oversight.
- **`DevDataSeeder`'s hardcoded default admin credentials are a known,
  explicit dev-only convenience**, not something that should ship as-is —
  a real system provisions its first admin out-of-band. Flagged loudly here
  and in the class's own Javadoc rather than left to be discovered.
- **H2 console is exposed at `/h2-console` with `permitAll`** — standard and
  harmless for local dev against an in-memory database, but this line (and
  the corresponding `frameOptions(sameOrigin)` relaxation) must not ship to
  any real deployment as-is.
- **No CORS configuration** — fine for a backend-only submission with no
  browser frontend attached (explicitly out of scope); would need explicit
  origin allow-listing before any frontend could call this cross-origin.
- **No rate limiting / brute-force protection** on `/api/auth/register` or
  the HTTP Basic login path — "production-grade observability/monitoring" is
  explicitly out of scope, and this falls under the same umbrella.
- **Discount code / redemption-count race** is handled (see Concurrency
  Handling), but a *failed* payment does not return the consumed redemption
  — documented above as an accepted, deliberate simplification, not missed.

## What's Not Done / Known Gaps
Expected and acknowledged, given the 48h window:
- No controller-level (MockMvc) tests — only service-layer unit/integration tests.
- Deleting a `Show` doesn't check for or cascade-cancel existing bookings against it.
- No "replace seat layout" endpoint — a screen's layout can only be created once (the unique constraint on `Seat` prevents silent duplication); changing a layout means deleting and recreating the screen.
- No email verification, password reset, or account lockout flow — reasonable given "advanced authentication" is explicitly out of scope.
- No pagination on list endpoints (`GET /api/admin/cities`, etc.) — fine at take-home data volumes, would matter in production.
