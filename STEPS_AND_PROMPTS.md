# Steps + Exact Prompts to Finish This Assignment

You have a working skeleton already: all entities, repositories, the core
`BookingService` with concurrency-safe seat holding, a pricing stub, an async
notification stub, a scheduled hold-expiry job, basic security config, and a
concurrency test that proves double-booking is prevented. That's the hardest
20% done. Below is the order to do the rest in, with a copy-pasteable prompt
for each step. Paste each prompt into Claude/ChatGPT/Cursor **along with the
relevant existing file(s) open or pasted in** — don't ask it to write blind.

Budget roughly: Day 1 = steps 1-5 (core flows working end to end). Day 2 =
steps 6-11 (admin, tests, polish, video).

---

## Step 0 — Get it compiling and running
```
cd movie-booking
mvn clean install
mvn spring-boot:run
```
Fix any version mismatches (Lombok/Java version in your IDE) before writing
new code. Commit this as your first commit.

## Step 1 — DTOs and request validation
**Prompt:**
> Here is my entity model [paste Booking.java, Show.java, ShowSeat.java, Seat.java].
> Create request/response DTOs for: creating a booking (show id + list of seat ids
> + optional discount code), a booking response (id, status, total amount, seat
> labels, show details), and a seat availability response for a show (seat id,
> row, number, tier, status). Use Java records. Add Jakarta Bean Validation
> annotations (@NotNull, @NotEmpty, @Size) on the request DTOs. Also give me a
> mapper (plain static methods, no MapStruct needed) from entity to response DTO.

## Step 2 — Controllers for the customer flow
**Prompt:**
> Here is my BookingService.java and the DTOs above. Write a
> BookingController with endpoints: GET /api/shows/{showId}/seats (availability),
> POST /api/bookings (hold seats — calls holdSeats), POST /api/bookings/{id}/confirm
> (calls confirmBooking after "payment"), GET /api/bookings/{id}, GET /api/bookings
> (current user's history — assume a getCurrentUserId() helper from Spring
> Security's Authentication). Use constructor injection, return proper HTTP status
> codes (201 for create, 404 for not found), and add a @RestControllerAdvice that
> maps SeatUnavailableException -> 409 Conflict, IllegalArgumentException -> 400,
> BookingNotFoundException -> 404, with a consistent JSON error body {timestamp,
> status, error, message}.

## Step 3 — Payment stub + confirm flow
**Prompt:**
> Here is my Booking, Payment, and BookingService (holdSeats/confirmBooking).
> Write a PaymentService with a processPayment(Long bookingId) method that: loads
> the booking, checks it's PENDING_PAYMENT and not expired, simulates a payment
> (always succeeds, or fails if amount > some absurd threshold for testing), saves
> a Payment record, and calls bookingService.confirmBooking(...) on success. On
> failure, leave the booking PENDING_PAYMENT so the hold-expiry job cleans it up
> naturally. Wire this into the POST /api/bookings/{id}/confirm endpoint.

## Step 4 — Pricing edge cases
**Prompt:**
> Here is my PricingService.java (stub). I need it to fully handle: multiple
> pricing tiers (regular/premium/weekend — weekend and premium can both apply to
> the same seat), and discount codes with either percentage-off or flat-amount-off,
> respecting validFrom/validUntil and maxRedemptions. Write the redemption-count
> increment safely considering concurrent bookings could use the same code at once
> — should I lock the DiscountCode row too, similar to how ShowSeat is locked in
> BookingService? Show me that version. Also write unit tests for: regular seat
> weekday, premium seat weekend, percentage discount, flat discount, expired code
> rejected, redemption limit rejected.

## Step 5 — Cancellation + refunds
**Prompt:**
> Here is my Booking, RefundPolicy, and Payment entities, plus BookingService.
> Write a CancellationService with a cancelBooking(Long bookingId, Long userId)
> method that: verifies the booking belongs to the user and is CONFIRMED, finds
> the applicable RefundPolicy tier based on hours between now and the show's
> startTime, computes the refund amount from the Payment, marks the Payment
> REFUNDED or PARTIALLY_REFUNDED, sets Booking status to CANCELLED, and releases
> the ShowSeat rows back to AVAILABLE (clear booking reference). Wrap it in
> @Transactional. Add a DELETE /api/bookings/{id} endpoint that calls it, and have
> it fire notificationService.sendCancellationNotice() async afterward.

## Step 6 — Admin CRUD + RBAC
**Prompt:**
> Here is my SecurityConfig.java and entities for City, Theater, Screen, Seat,
> Movie, Show, DiscountCode, RefundPolicy. Write admin-only REST controllers
> under /api/admin/** for full CRUD on each (cities, theaters, screens+seat
> layout bulk-create, movies, shows, discount codes, refund policies). For
> creating a Screen, also accept a seat layout spec (rows, seats per row, which
> rows are premium) and bulk-create the Seat rows. When a Show is created, also
> bulk-create the corresponding ShowSeat rows (one per Seat on that screen, status
> AVAILABLE). Use DTOs, not raw entities, in request bodies.

## Step 7 — Auth (register/login) within RBAC scope
**Prompt:**
> Note: OAuth/SSO/MFA are explicitly out of scope for this assignment — I only
> need basic authentication. Here is my User entity and SecurityConfig (HTTP
> Basic). Write a minimal AuthController with POST /api/auth/register (creates a
> CUSTOMER user, hashes password with the existing BCryptPasswordEncoder bean)
> and confirm HTTP Basic is sufficient for POST /api/auth/login — I don't need a
> token endpoint since this is out of scope. Show me how a client would call a
> protected endpoint with Basic auth for testing (curl example).

## Step 8 — Input validation and global error handling polish
**Prompt:**
> Here are all my controllers. Make sure every request DTO has appropriate
> Jakarta validation annotations, @Valid is used in every controller method that
> accepts a body, and my @RestControllerAdvice (from step 2) also handles
> MethodArgumentNotValidException (400, with field-level error messages) and a
> catch-all Exception -> 500 that doesn't leak stack traces to the client.

## Step 9 — Tests (beyond the concurrency one you already have)
**Prompt:**
> Here is BookingService, PricingService, CancellationService, and the
> concurrency test I already have (BookingServiceConcurrencyTest). Write:
> unit tests for PricingService (mocked repo) covering the pricing/discount
> matrix, unit tests for CancellationService covering each refund-policy tier,
> and a @SpringBootTest integration test for the full happy path (hold -> confirm
> -> appears in booking history) and the full expiry path (hold -> wait/simulate
> expiry -> seat becomes AVAILABLE again). Use JUnit 5 and AssertJ.

## Step 10 — README + Agents.md/Claude.md (both required submissions)
**Prompt:**
> Here is my README.md draft [paste it] and a summary of what I built [paste a
> bullet list of your actual entities/endpoints/decisions]. Fill in every
> placeholder section — Architecture Overview, Assumptions, API Overview, AI
> Workflow Used, Testing Approach, What's Not Done — in clear technical prose,
> 150-300 words per section. Then write a separate Claude.md/Agents.md file
> documenting: the AI tool(s) I used, the general workflow (skeleton first,
> then flow-by-flow prompts), and 2-3 examples of prompts I used verbatim and
> what I changed after reviewing the output — this file itself is a required
> submission per the assignment.
*(Be honest here — list the actual prompts from this file that you used and what you edited.)*

## Step 11 — Video script (max 10 min)
**Prompt:**
> Here is my final README.md. Write me a tight spoken outline (not a
> word-for-word script) for a 10-minute recorded walkthrough covering, in this
> order: (1) 60s - the problem and my entity model / why ShowSeat is separate
> from Seat, (2) 2 min - live demo of the concurrency test failing double-booking,
> (3) 90s - tech stack and why, (4) 2 min - AI workflow, what I generated vs wrote,
> (5) 90s - testing approach, (6) 90s - assumptions and what I'd do with more time.
> Keep each section to bullet points I can speak from, not prose to read.

---

## A note on how to use these prompts well
- Always paste the actual current file(s), not just describe them — the model
  should extend what exists, not invent a parallel version.
- After each generated piece, read it before moving to the next prompt. If
  something looks wrong (e.g. a missing lock, a N+1 query), ask a follow-up
  in the same thread rather than accepting it blind — and note that back-and-forth
  in your Claude.md as part of the "AI workflow."
- Multiple small commits (after each step above) will look much better in
  your repo history than one giant commit at the end — the assignment
  explicitly says "multiple commits are expected."
