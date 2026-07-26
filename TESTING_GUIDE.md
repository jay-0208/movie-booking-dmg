# Testing Guide — Step by Step

Two layers to test: (1) the automated test suite, which is the evidence for
your video, and (2) manually exercising the API with curl, which is how
you'll actually demo it and reassure yourself it behaves as documented.

## Prerequisites
```
java -version   # need 17+
mvn -version    # need Maven 3.8+
```
If either is missing, install a JDK 17 and Maven before anything else.

---

## Part 1 — Automated tests (do this first)

```
cd movie-booking
mvn clean test
```

Expect all 4 test classes to pass:
- `BookingServiceConcurrencyTest` — 10 threads race for one seat; asserts
  exactly 1 succeeds. **This is the test to point at in your video** for the
  "no double-allocation" requirement.
- `PricingServiceTest` — 9 cases covering the pricing/discount matrix.
- `CancellationServiceTest` — refund tiers, fail-closed default, seat release.
- `BookingLifecycleIntegrationTest` — full hold→confirm, hold→expire→re-bookable,
  hold→confirm→cancel, double-confirm-rejected round trips.

If any test fails, read the assertion message before touching code — a few
things worth knowing in advance:
- `BookingLifecycleIntegrationTest` uses real `LocalDateTime.now()` calls a
  few milliseconds apart; this is accounted for in the design (see comments
  in that file) but if your machine is under heavy load, timing-sensitive
  tests could theoretically flake — rerun once before assuming a real bug.
- If `BookingServiceConcurrencyTest` ever shows more than 1 success, that's
  a real concurrency bug, not a flake — stop and investigate the lock.

---

## Part 2 — Run the app

```
mvn spring-boot:run
```

Watch the startup logs for this block — **copy the admin credentials from
here, they're randomly nothing special but you need them for Part 3**:
```
=================================================================
Seeded default admin user for local/demo use:
  email:    admin@moviebooking.local
  password: ChangeMe123!
Use these with HTTP Basic auth against /api/admin/** endpoints.
=================================================================
```

Confirm it's up:
```
curl -i http://localhost:8080/api/shows/1/seats
```
You should get a `200` with an empty JSON array `[]` (no shows exist yet) —
not a connection error. That confirms the app is running.

Optional — inspect the database directly at any point:
open `http://localhost:8080/h2-console` in a browser, JDBC URL
`jdbc:h2:mem:moviebooking`, username `sa`, blank password.

---

## Part 3 — Set up a bookable show (admin flow)

Every command below uses HTTP Basic with the seeded admin. Save IDs from
each response — you'll need them in the next command.

**3.1 Create a city**
```
curl -s -u admin@moviebooking.local:ChangeMe123! \
  -X POST http://localhost:8080/api/admin/cities \
  -H "Content-Type: application/json" \
  -d '{"name":"Indore","state":"Madhya Pradesh"}'
```
→ note the returned `"id"` as `CITY_ID`.

**3.2 Create a theater in that city**
```
curl -s -u admin@moviebooking.local:ChangeMe123! \
  -X POST http://localhost:8080/api/admin/theaters \
  -H "Content-Type: application/json" \
  -d '{"name":"PVR Central Mall","address":"MG Road","cityId":CITY_ID}'
```
→ note `THEATER_ID`.

**3.3 Create a screen in that theater**
```
curl -s -u admin@moviebooking.local:ChangeMe123! \
  -X POST http://localhost:8080/api/admin/screens \
  -H "Content-Type: application/json" \
  -d '{"name":"Screen 1","theaterId":THEATER_ID}'
```
→ note `SCREEN_ID`.

**3.4 Create the seat layout for that screen**
```
curl -s -u admin@moviebooking.local:ChangeMe123! \
  -X POST http://localhost:8080/api/admin/screens/SCREEN_ID/seat-layout \
  -H "Content-Type: application/json" \
  -d '{"rows":[
        {"rowLabel":"A","seatCount":10,"tier":"PREMIUM"},
        {"rowLabel":"B","seatCount":12,"tier":"REGULAR"}
      ]}'
```
→ response is a list of 22 created seats. Note one seat's `"id"` from row A
as `SEAT_ID` (you'll book this one later).

**3.5 Create a movie**
```
curl -s -u admin@moviebooking.local:ChangeMe123! \
  -X POST http://localhost:8080/api/admin/movies \
  -H "Content-Type: application/json" \
  -d '{"title":"Test Movie","language":"EN","durationMinutes":120,"genre":"Drama"}'
```
→ note `MOVIE_ID`.

**3.6 Create a show** (this bulk-creates the bookable `ShowSeat` rows)
```
curl -s -u admin@moviebooking.local:ChangeMe123! \
  -X POST http://localhost:8080/api/admin/shows \
  -H "Content-Type: application/json" \
  -d '{"movieId":MOVIE_ID,"screenId":SCREEN_ID,
       "startTime":"2026-08-01T18:00:00","endTime":"2026-08-01T20:00:00",
       "basePrice":200}'
```
→ note `SHOW_ID`.

**3.7 Verify seats are bookable**
```
curl -s http://localhost:8080/api/shows/SHOW_ID/seats
```
→ should return 22 seats, all `"status":"AVAILABLE"`. This call needs no
auth — it's public browsing per `SecurityConfig`.

---

## Part 4 — Customer booking flow

**4.1 Register a customer**
```
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@test.com","password":"password123","name":"Your Name"}'
```

**4.2 Hold a seat**
```
curl -s -u you@test.com:password123 \
  -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{"showId":SHOW_ID,"seatIds":[SEAT_ID]}'
```
→ `201 Created`, status `PENDING_PAYMENT`, note `BOOKING_ID`. Since seat A1
is `PREMIUM` and the show is likely a weekday, expect `totalAmount` around
`360.00` (200 × 1.8 premium multiplier) — adjust your mental math if you
picked a weekend date in step 3.6.

Re-check seat availability — that seat should now show `"status":"HELD"`:
```
curl -s http://localhost:8080/api/shows/SHOW_ID/seats
```

**4.3 Confirm (simulated payment)**
```
curl -s -u you@test.com:password123 \
  -X POST http://localhost:8080/api/bookings/BOOKING_ID/confirm
```
→ status flips to `CONFIRMED`. Re-check seat availability again — now
`"status":"BOOKED"`.

**4.4 View booking history**
```
curl -s -u you@test.com:password123 http://localhost:8080/api/bookings
```

**4.5 Cancel it (tests the refund flow)**
```
curl -s -u you@test.com:password123 \
  -X DELETE http://localhost:8080/api/bookings/BOOKING_ID
```
→ status `CANCELLED`. Note: without an active `RefundPolicy` configured (you
haven't created one yet), this will refund **0%** — that's the fail-closed
default working as documented, not a bug. To see a real refund percentage,
create a policy first:
```
curl -s -u admin@moviebooking.local:ChangeMe123! \
  -X POST http://localhost:8080/api/admin/refund-policies \
  -H "Content-Type: application/json" \
  -d '{"name":"Standard","minHoursBeforeShow":24,"refundPercentage":100}'
```
then repeat the hold → confirm → cancel sequence with a fresh seat and check
`payment.refundedAmount` reflects it (note: there's no `GET /payments`
endpoint currently, so confirm this by checking the H2 console's `PAYMENT`
table directly, or add a quick log statement if you want to see it via curl).

---

## Part 5 — Prove the concurrency requirement manually (optional, beyond the automated test)

Hold a *different* seat with two requests fired as close together as
possible:
```
curl -s -u you@test.com:password123 -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" -d '{"showId":SHOW_ID,"seatIds":[SEAT_ID_2]}' &
curl -s -u you@test.com:password123 -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" -d '{"showId":SHOW_ID,"seatIds":[SEAT_ID_2]}' &
wait
```
One response should be `201 Created`; the other should be a `409 Conflict`
with a `SeatUnavailableException` message. This is much less rigorous than
the automated 10-thread test — it's a nice live demo for the video, not a
substitute for `BookingServiceConcurrencyTest`.

---

## Part 6 — Verify error handling and validation

```
# Missing required field -> 400 with fieldErrors
curl -i -u you@test.com:password123 -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" -d '{}'

# Booking a seat that doesn't exist -> 409 SeatUnavailableException
curl -i -u you@test.com:password123 -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" -d '{"showId":SHOW_ID,"seatIds":[999999]}'

# Accessing another user's booking -> 404, not 403
curl -i -u you@test.com:password123 http://localhost:8080/api/bookings/999999

# Non-admin hitting an admin endpoint -> 403
curl -i -u you@test.com:password123 http://localhost:8080/api/admin/cities
```

---

## Part 7 — Verify the expiry sweep (optional, takes ~1 min real time)

1. Hold a seat but don't confirm it.
2. Temporarily set `app.seat-hold.expiry-minutes: 0` (or just wait 5 minutes
   at the default) in `application.yml`, restart, hold again.
3. Wait ~30–60 seconds (the sweep job runs every 30s).
4. Check seat availability again — it should have flipped back to
   `AVAILABLE`, and `GET /api/bookings/{id}` should show `EXPIRED`.

This is already covered deterministically (without waiting) in
`BookingLifecycleIntegrationTest`, so this manual step is purely for a live
demo if you want one in the video.

---

## Quick troubleshooting
- **`mvn` not found** — install Maven, or use your IDE's built-in Maven runner.
- **Port 8080 already in use** — another process is using it; stop it, or
  add `server.port=8081` to `application.yml`.
- **`401 Unauthorized` on admin calls** — double check you copied the exact
  seeded password from the startup log, not the placeholder in this guide
  (it's the same value, `ChangeMe123!`, but re-check for typos).
- **`403 Forbidden` on admin calls despite correct credentials** — this is
  the exact bug documented as "caught and fixed" in `CLAUDE.md`
  (`CustomUserDetailsService` missing). If you see this, check that class
  still exists and is a `@Service` bean picked up by component scanning.
