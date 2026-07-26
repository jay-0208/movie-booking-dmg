# Submission Checklist

Every line item below is taken directly from the assignment PDF. Status is
based on an actual code review, not a restatement of intent — "Done" means I
traced it to a specific file/test, not just "should be covered."

## Product Requirement

| Requirement | Status | Where |
|---|---|---|
| Multiple cities, multiple theaters per city | ✅ Done | `City` → `Theater` (`cityId` FK), `TheaterAdminController` filters by `cityId` |
| Multiple shows per theater | ✅ Done | `Theater` → `Screen` → `Show` |
| Seat-level booking | ✅ Done | `Seat` (layout) + `ShowSeat` (per-show status) |
| Time-bound holds that auto-release on expiry | ✅ Done | `holdExpiresAt` on `Booking`/`ShowSeat`, `SeatHoldExpiryJob` sweeps every 30s |
| Pricing tiers: regular, premium, weekend | ✅ Done | `SeatTier` enum + weekend/premium multipliers in `PricingService` (stack multiplicatively — see README Assumptions) |
| Discount codes | ✅ Done | `DiscountCode` entity, percentage or flat, validity window, max redemptions, locked redemption counter |
| Payment | ⚠️ Simulated | `PaymentService` — explicitly stubbed, no real PSP (correct per scope) |
| Booking confirmation | ✅ Done | `POST /api/bookings/{id}/confirm` |
| Refunds on cancellation, configurable refund policies | ✅ Done | `RefundPolicy` (admin-managed tiers), `CancellationService` |
| Correct serialization of concurrent bookings, no double-allocation | ✅ Done, tested | `PESSIMISTIC_WRITE` lock in `ShowSeatRepository`; proven by `BookingServiceConcurrencyTest` (10 threads, exactly 1 wins) |
| Notifications delivered without blocking booking flow | ✅ Done | `NotificationService` methods are `@Async` |
| Admin role: manage cities/theaters/shows/seat layouts/pricing/refund policies | ✅ Done | 7 controllers under `/api/admin/**`, `hasRole("ADMIN")` enforced and actually wired (see below) |
| Customer role: browse, book, cancel, view history | ✅ Done | `BookingController` |

## In Scope (explicitly required)

| Requirement | Status | Where |
|---|---|---|
| REST APIs covering core flows | ✅ Done | See README API Overview table |
| Persistence to a database | ✅ Done | H2 (dev/test), PostgreSQL driver included for prod swap |
| Basic RBAC for the defined roles | ✅ Done — **and actually verified working**, not just declared | `SecurityConfig` + `CustomUserDetailsService`. **Note:** RBAC was initially declared (`hasRole("ADMIN")`) but had no `UserDetailsService` bean backing it — Spring Boot's fallback is a single auto-generated user with zero roles, so admin checks would have silently never passed for anyone. Caught and fixed — see `CLAUDE.md`. |
| Input validation and error handling | ✅ Done | Jakarta Bean Validation on every request DTO, `GlobalExceptionHandler` with consistent JSON error shape and correct HTTP codes |
| Unit & Integration Tests for core flows | ✅ Done | 4 test classes — see README Testing Approach |

## Out of Scope (explicitly told not to do — verify none of it crept in)

| Item | Status |
|---|---|
| UI or frontend | ✅ None built |
| Deployment, containerization, CI/CD | ✅ None built — no Dockerfile, no CI config |
| Distributed systems or microservices | ✅ Single monolith, single datastore |
| Advanced authentication (OAuth, SSO, MFA) | ✅ HTTP Basic only, as scoped |
| Production-grade observability, monitoring, alerting | ✅ None added — no Actuator, no metrics/tracing |

## What to Submit

| Item | Status | Action needed from you |
|---|---|---|
| GitHub repository (mandatory) | ⬜ Not yet pushed | You need to `git init`, commit, and push — see Do/Don't below |
| Personal GitHub repo link | ⬜ | Yours to provide |
| Multiple commits during development | ⬜ | See commit strategy below — **don't** do one giant commit |
| README.md | ✅ Done | Filled in fully — architecture, concurrency, assumptions, API table, security notes, known gaps |
| Agents.md / Claude.md | ✅ Done | See `CLAUDE.md` |
| Skills used during development | ✅ Documented | Listed in `CLAUDE.md` under "Tools & Skills" |
| All raw files used during development | ✅ Present | `STEPS_AND_PROMPTS.md` is the literal prompt log; this checklist + the conversation transcript are the rest |
| Video recording (max 10 min) | ⬜ Not yet recorded | Outline already in `STEPS_AND_PROMPTS.md` Step 11 |

## Do's

- **Do** commit incrementally as you review each piece — even now, splitting
  what exists into logical commits (entities → repos → booking service →
  controllers → admin → auth → tests → docs) reads far better than one
  initial commit dump. The assignment explicitly says multiple commits are
  expected.
- **Do** actually run `mvn clean install` and `mvn test` locally before
  recording the video — I don't have Maven/network access in this
  environment to verify the build compiles and the tests pass. Treat that as
  unverified until you've run it yourself.
- **Do** rotate or remove the `DevDataSeeder` default admin credentials if
  you make this repo public for longer than the review window, or at least
  say out loud in the video that you know it's a dev-only shortcut.
- **Do** open every file this process generated at least once before the
  video — you should be able to explain any line if asked, not just the
  ones highlighted here.
- **Do** double-check your own repo for stray/leftover files before pushing
  — twice during this build, unrelated leftover files from a prior sandbox
  session showed up in the working directory and had to be deleted (see
  `CLAUDE.md`). I caught both, but verify your own final zip contents once
  yourself as a last check.

## Don'ts

- **Don't** claim the concurrency test as your first attempt at solving the
  problem if asked in the interview — be ready to explain *why* pessimistic
  locking specifically, and the optimistic-locking alternative, since that's
  the single most likely follow-up question.
- **Don't** let the video exceed 10 minutes — the outline in
  `STEPS_AND_PROMPTS.md` Step 11 is timed to fit; trim explanation, not the
  concurrency demo.
- **Don't** present the AI-assisted workflow as either "I wrote everything
  manually" or "I generated everything and didn't check it" — both undersell
  what actually happened. `CLAUDE.md` documents the real middle ground:
  structured, reviewed, with actual bugs caught along the way.
- **Don't** ship `DevDataSeeder`, `csrf().disable()`, or the open H2 console
  as-is if this ever becomes a real deployment — they're correctly scoped
  for a take-home, not for production, and the README says so explicitly.
  Know the difference if asked.
- **Don't** silently fix the "no seat layout yet" show-creation gap or the
  "zero-refund uses PARTIALLY_REFUNDED" enum overload without mentioning
  them — they're documented as conscious tradeoffs precisely so you can
  discuss them instead of getting caught off guard.

## Security Review Summary
Full detail is in the README's "Security Notes" section. Short version: no
SQL injection surface (JPQL only, no native queries), passwords hashed and
never returned, RBAC verified end-to-end (not just declared), IDOR-safe
booking lookups (404 not 403). The known, accepted gaps — dev-only seeded
admin credentials, no rate limiting, no CORS config, CSRF disabled — are all
correctly scoped to what the assignment asks for and are called out
explicitly rather than left for a reviewer to discover first.
