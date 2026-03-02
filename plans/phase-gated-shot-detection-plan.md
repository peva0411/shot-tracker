# Phase-Gated Shot Detection — Design Plan

## Problem Statement

The current `ShotAnalyzer` fires on a single frame: ball overlaps hoop zone + moving downward + was
above hoopTop in the last 800ms. It has no notion of _what the ball was doing before it arrived at
the hoop_. This makes it impossible to distinguish:

| Scenario | Current behaviour |
|---|---|
| Real shot — full arc from player's hands | ✅ counts correctly |
| Bounce-back (MADE) — ball passes through twice | ❌ double-counts after cooldown |
| Rim bounce (MISSED) — ball clips rim, re-enters | ❌ double-counts if it re-enters after cooldown |
| Horizontal pass or roll through hoop zone | ❌ can fire if arc check passes by coincidence |
| Pump fake that briefly dips into hoop zone | ❌ may fire (ball moving downward + prior arc) |

The fundamental limitation: the "was above hoopTop in the last 800ms" arc check is satisfied by
_any_ ball that was recently high enough — including the bounce arc of a ball that already went
through the hoop on the previous shot. No time-based cooldown alone can reliably close this gap
because the bounce return time depends on hoop height (variable for mini vs full-size hoops).

---

## Proposed Solution: Phase-Gated State Machine

Replace `ShotAnalyzer` with a `PhaseGatedShotDetector` that requires the ball to complete a
verified parabolic arc cycle before counting a shot. A detection is only permitted when the
machine has _observed the entire arc_ — rise, peak above hoopTop, descent, entry — in sequence
during the current cycle. A bounce-back or stale trajectory cannot satisfy this because the
machine resets after each shot and requires fresh phase transitions.

---

## State Definitions

```
IDLE
  │  Ball seen moving upward (velocity < -MIN_ASCENDING_VELOCITY)
  ▼
ASCENDING
  │  Velocity crosses zero (upward→downward) while ball is above hoopTop
  │  — record peakY and peakTime
  ▼
PEAKED          ← ball is now confirmed above hoopTop and descending
  │  Ball overlaps hoop zone AND velocity ≥ MIN_ENTRY_VELOCITY
  ▼
ENTERING        ← brief confirmation window; ball must remain in zone
  │  Ball still in hoop zone after MIN_CONFIRMATION_MS
  ▼
SHOT_FIRED      ← ShotEvent emitted, outcome coroutine running
  │  Outcome window (600ms) completes
  ▼
LOCKOUT         ← duration varies by outcome
  │  Lockout expires AND ball is not in hoop zone
  ▼
IDLE
```

### Reset Conditions — any state → IDLE immediately

| From state | Trigger |
|---|---|
| Any | Ball not detected for > `TRACKING_LOSS_MS` (500ms) |
| ASCENDING | Ball in ASCENDING for > `MAX_ASCENT_DURATION_MS` (3 000ms) without peaking |
| ASCENDING | Ball reverses to downward before reaching above hoopTop (too weak, not a shot) |
| PEAKED | Ball re-ascends strongly (velocity < -REASCENT_THRESHOLD) — shot was aborted |
| PEAKED | Time since peak > `MAX_DESCENT_WAIT_MS` (2 000ms) without entering hoop zone |
| ENTERING | Ball leaves hoop zone without confirmation — continue in PEAKED (or reset if stale) |
| LOCKOUT | Ball detected in hoop zone while lockout is active — extend lockout slightly as safety |

---

## Per-Phase Data Captured

The detector carries a small `ArcContext` object that accumulates kinematic metadata as each
phase is entered. This feeds directly into `ShotEvent.initialConfidence` scoring.

| Field | Captured at | Used for |
|---|---|---|
| `ascendStartY` | IDLE → ASCENDING | Assert ball came from below hoop |
| `ascendStartTime` | IDLE → ASCENDING | Compute total flight time |
| `peakY` | ASCENDING → PEAKED | Arc height above hoopTop; confidence boost |
| `peakTime` | ASCENDING → PEAKED | Peak-to-entry time (shot pace indicator) |
| `entryVelocity` | PEAKED → ENTERING | Confidence scoring; sanity filter |
| `entryOverlap` | PEAKED → ENTERING | Confidence scoring |

### Confidence Scoring (revised)

```
base               = 0.30
+ arcHeight        = (hoopTop - peakY).coerceAtLeast(0f) * 0.60   // higher arc → more confident
+ entryOverlap     = (overlap - 0.25f) * 0.80
+ velocityQuality  = if (entryVelocity in 0.1..2.5) +0.10
+ fullCycleBonus   = +0.15  // always present — arc cycle was fully validated
```

---

## Code Changes

### New files

| File | Purpose |
|---|---|
| `camera/detector/ShotPhase.kt` | `enum class ShotPhase { IDLE, ASCENDING, PEAKED, ENTERING, SHOT_FIRED, LOCKOUT }` |
| `camera/detector/ArcContext.kt` | Data class accumulating per-arc kinematic fields |
| `camera/detector/PhaseGatedShotDetector.kt` | Full state machine; replaces `ShotAnalyzer` |

### Modified files

| File | Change |
|---|---|
| `TrajectoryTracker.kt` | Add `peakPositionSince(ms)` helper — returns min-centerY position in window; add `velocityAt(startMs, endMs)` for fine-grained velocity over a sub-window |
| `SessionViewModel.kt` | Swap `ShotAnalyzer` → `PhaseGatedShotDetector`; expose `currentPhase: ShotPhase` in `SessionUiState`; call `notifyOutcome(outcome)` (replaces `notifyMade()`) so MISSED also sets a lockout duration |
| `SessionUiState.kt` | Add `currentPhase: ShotPhase = ShotPhase.IDLE` |
| `SessionScreen.kt` | Show current phase in debug overlay (colour-coded badge); log arc height at peak |

### Retained / deleted

`ShotAnalyzer.kt` — keep in the codebase but deprecated, hidden behind a feature flag initially.
Remove once field testing confirms the new detector is superior.

---

## Configuration Constants

All constants live at the top of `PhaseGatedShotDetector.kt`. Candidates for future
in-app tuning (same pattern as the existing frame-skip slider):

```kotlin
MIN_ASCENDING_VELOCITY  = 0.08f   // normalised/sec; upward speed to enter ASCENDING
REASCENT_THRESHOLD      = 0.06f   // if ball re-ascends above this speed → abort shot
MIN_ENTRY_VELOCITY      = 0.05f   // downward speed required at hoop entry
MIN_PEAK_MARGIN         = 0.02f   // ball must clear hoopTop by this much (avoids grazing arcs)
MIN_CONFIRMATION_MS     = 60L     // ball must be in hoop zone continuously before shot fires
TRACKING_LOSS_MS        = 500L    // ball missing this long → back to IDLE
MAX_ASCENT_DURATION_MS  = 3000L   // timeout on ASCENDING phase
MAX_DESCENT_WAIT_MS     = 2000L   // timeout waiting for hoop entry after peak
MADE_LOCKOUT_MS         = 2500L   // lockout after confirmed MADE
MISSED_LOCKOUT_MS       = 800L    // lockout after confirmed MISSED
AMBIGUOUS_LOCKOUT_MS    = 1200L   // lockout after AMBIGUOUS
```

---

## Handling the Bounce-Back — Why This Works

After a MADE shot:

1. `SHOT_FIRED → LOCKOUT` immediately. State resets to `IDLE` only after lockout **and** ball
   is outside hoop zone.
2. Lockout duration is outcome-driven (2 500ms for MADE), not fixed.
3. When the state eventually resets to `IDLE`, the ball must restart the full cycle:
   `IDLE → ASCENDING`. For a legitimate next shot, the player has the ball in their hands.
   For a bounce-back that's still settling, the ball is unlikely to be in a clean ASCENDING
   trajectory from below.
4. Critically: the `ASCENDING → PEAKED` transition requires the velocity to _cross zero while
   above hoopTop_ **during this cycle** — stale data from the previous shot's arc carries zero
   weight because the state machine has explicitly reset.

---

## `TrajectoryTracker` Additions

```kotlin
// Returns the highest (min centerY) position seen in the last `withinMs` ms.
// Used by the detector to confirm peakY after velocity crosses zero.
fun peakPositionSince(withinMs: Long): BallPosition?

// Returns vertical velocity computed over a specific historical sub-window
// rather than always the most recent 500ms. Allows the detector to check
// the velocity at the _start_ of ASCENDING to confirm the ball was rising.
fun velocityOverWindow(fromMs: Long, toMs: Long): Float?
```

The existing `verticalVelocity()` (last 500ms) is still used during PEAKED/DESCENDING/ENTERING.

---

## Debug Overlay Additions

- **Phase badge**: colour-coded label showing current `ShotPhase`.
  - IDLE → grey, ASCENDING → yellow, PEAKED → green, ENTERING → amber, SHOT_FIRED/LOCKOUT → red
- **Arc height indicator**: at PEAKED transition, log and display how far (in normalised units)
  the ball cleared hoopTop. Useful for diagnosing why a slow lob failed the `MIN_PEAK_MARGIN`
  check.
- Both items are debug-only (behind the existing bug-icon toggle).

---

## Assumptions & Risks

| # | Item | Mitigation |
|---|---|---|
| 1 | Camera is placed **below** the hoop looking up, so Y increases downward and hoopTop < hoopBottom | Document assumption; add a sanity-check assertion in debug builds |
| 2 | Kalman-predicted frames can create artificial velocity readings at arc transition boundaries | The `MIN_CONFIRMATION_MS` window and phase-timeout guards absorb single-frame anomalies |
| 3 | At low effective framerates (7 fps with FRAME_SKIP=2) the velocity-zero crossing might be missed in the same frame | Detect the crossing by comparing the _last two velocity samples_, not a single frame |
| 4 | Very slow lob shots may never reach `MIN_ASCENDING_VELOCITY` | Tune constant down; alternatively enter ASCENDING on any sustained upward movement over 200ms |
| 5 | Shots from directly under the hoop (mini-hoop dunks / layups from below) will never enter ASCENDING because the ball goes straight up through the hoop | Acknowledge as out of scope; these are uncommon and hard to distinguish from random upward passes |
| 6 | A full-size hoop has a longer bounce-return time (~1.8s) than a mini hoop (~1.2s); MADE_LOCKOUT_MS must cover both | 2 500ms post-outcome covers both; no per-hoop tuning needed |

---

## Validation Plan

1. **Unit tests** for `PhaseGatedShotDetector` using synthetic `BallPosition` sequences:
   - Valid shot: IDLE → ASCENDING → PEAKED → ENTERING → SHOT_FIRED ✓
   - Bounce-back: first shot fires, lockout period, ball re-enters — should NOT fire ✓
   - Quick miss + retry (2 separate arcs, 2.5s apart) — should fire twice ✓
   - Pump fake (ball enters hoop zone on upswing, velocity negative) — should NOT fire ✓
   - Horizontal pass through hoop zone (no arc) — should NOT fire (never leaves IDLE) ✓
   - Slow lob (low velocity, long arc) — should fire ✓
   - Tracking loss mid-arc — resets to IDLE, does not fire ✓

2. **Feature flag** (e.g. `DetectionPreferences.usePhaseDetector: Boolean`) to A/B switch
   between old `ShotAnalyzer` and new `PhaseGatedShotDetector` during field testing.

3. **Field testing checklist**:
   - Mini door hoop: rapid fire (3–4 shots/minute), no doubles
   - Mini door hoop: made basket, immediate re-shot at < 3s — should count second shot
   - Full-size hoop (if available): standard shooting range
   - Edge case: dribble ball next to hoop without shooting — should not count

---

## Open Questions Before Implementation

1. **Should `ShotPhase` be exposed to the UI as part of `SessionUiState`**, or only logged?
   (Recommendation: expose it — the debug overlay phase badge is very useful for tuning.)

2. **Should `MIN_PEAK_MARGIN` be user-configurable** in the debug overlay, or keep it constant?
   (Recommendation: constant for now, add slider if field testing shows it needs tuning.)

3. **What happens to `notifyMade()` / `notifyOutcome()`** — the ViewModel currently calls this
   on `ShotAnalyzer`. The new detector should accept `notifyOutcome(ShotOutcome)` to set the
   appropriate lockout, replacing the current MADE-only signal.
