# Claude.md — AI Workflow Documentation

## Tool
Claude (Anthropic), used conversationally through step-by-step collaborative
development — not a single one-shot generation.

## Why this workflow, not a single mega-prompt
A 48-hour, open-ended, feature-rich system like this has too many
interlocking decisions (locking strategy, entity boundaries, RBAC wiring,
refund tier resolution) to hand to an LLM as one prompt and accept the output
blind. The approach taken instead:

1. **Skeleton first.** Before writing a single business-logic prompt, the
   entire entity/repository/exception layer was generated and reviewed as a
   unit — this is the layer everything else depends on, so getting it
   structurally right (in particular, the `Seat` vs `ShowSeat` split — see
   README Architecture) before building on top of it mattered more than
   moving fast.
2. **One flow at a time, in dependency order**: booking hold → DTOs/mapper
   → controller/error-handling → payment → cancellation/refunds → pricing
   edge cases → admin CRUD + RBAC → auth → validation polish → tests →
   documentation. Each step's prompt included the actual current state of
   the relevant files, not a description of them, so the model extended real
   code instead of inventing a parallel version that would need reconciling
   later.
3. **Review before advancing.** After every step, the generated code was read
   — not just accepted — before moving to the next prompt. This is not a
   formality: it's what caught the three real issues below, none of which
   would have been visible from output that merely "looked plausible."

The exact prompt used at each step is in `STEPS_AND_PROMPTS.md`, which was
itself generated as part of this process specifically so the workflow would
be reproducible and auditable rather than living only in chat history.

## Issues caught during review (not hypothetical — actually found and fixed)

**1. RBAC was declared but not wired.** `SecurityConfig` had
`hasRole("ADMIN")` on `/api/admin/**` from an early step, and it *looked*
complete. Only when actually building the admin controllers in a later step
did a check reveal there was no `UserDetailsService` bean anywhere in the
project — meaning Spring Boot was silently falling back to a single
auto-generated in-memory user with no roles, and `hasRole("ADMIN")` could
never have passed for anyone, including a real admin. This is exactly the
kind of gap that a superficial glance at "security config exists" would
miss. Fixed by adding `CustomUserDetailsService` to bridge the `User` entity
into Spring Security, and `DevDataSeeder` to solve the resulting
chicken-and-egg problem of having no way to reach the now-actually-locked-down
admin endpoints on a fresh database.

**2. Admin CRUD 404s were falling through to generic 500s.** An early admin
controller draft used `NoSuchElementException` for "not found," which
`GlobalExceptionHandler` had no specific mapping for — it would have hit the
catch-all `Exception → 500` handler instead of returning a proper 404. Caught
on review, fixed by introducing `ResourceNotFoundException` with its own
handler, and used consistently across all seven admin controllers.

**3. Discount amount fields had no range validation.** `DiscountCodeRequest`
validated that a code string was present but not that `percentageOff` was
actually between 0–100 or that `flatAmountOff` was non-negative — meaning a
value like `percentageOff: 500` would have sailed through Bean Validation
and only surfaced as a silently-wrong (or negative) total deep inside
`PricingService`. Caught during the dedicated validation-polish pass and
fixed at the DTO boundary, where it belongs.

**4. Leftover files from an unrelated session, twice.** Partway through the
build, and again a few steps later, files surfaced in the working directory
that didn't match the package structure or content being built (referencing
a `dto.admin` package that was never part of this design). These were
identified as stale artifacts from an unrelated prior sandbox session — not
generated as part of this work — and deleted both times rather than left in
place or silently merged with the real code. Worth a final manual check of
your own copy before pushing, for the same reason.

## Example prompt → output → review cycle

**Prompt used (Step 4, pricing):**
> Here is my PricingService.java (stub). I need it to fully handle: multiple
> pricing tiers (regular/premium/weekend — weekend and premium can both apply
> to the same seat), and discount codes with either percentage-off or
> flat-amount-off... Also write unit tests for: regular seat weekday, premium
> seat weekend, percentage discount, flat discount, expired code rejected,
> redemption limit rejected.

**What came back:** a working multiplicative weekend+premium calculation and
discount application logic.

**What was changed after review:** the discount redemption counter in the
first pass wasn't safe under concurrent use — two users spending the last
redemption of a limited code at the same instant could both succeed. This was
caught by applying the same scrutiny already given to the seat-locking code
in an earlier step (i.e., "does this have the same race condition as the
thing I already fixed?") and fixed by adding a `PESSIMISTIC_WRITE` lock on
the `DiscountCode` row, mirroring the seat-lock pattern exactly rather than
inventing a second locking strategy.

## Tools & Skills
- Claude, conversational/iterative mode (no autonomous agent loop — every
  step was a discrete, reviewed prompt/response pair)
- Standard Spring Boot/JPA/Maven knowledge, no custom skill files or
  plugins beyond the base model
- No code was accepted without being read; no test was accepted without
  understanding why it passes, not just that it does

## What this means for the video
Per the assignment's own framing — "the AI workflow used" is one of the
things being directly evaluated, not incidental to it. The four issues above
are the honest answer to "what did you actually catch," and are worth
walking through briefly in the recording rather than glossed over — they
demonstrate engineering judgment applied *to* AI output, which is the actual
skill being assessed here, not typing speed.
