# Component Audit — QuietPing `ui/components/`

> Read-only audit of the 9 shared Compose components (919 LOC total). Goal: find *better* components —
> for reuse, maintainability, and design-pattern adherence — **without losing the LiquidGlass aesthetic,
> the motion vocabulary, or the Haze blur.** No code changed yet (see §5 gate).

## 1. Central thesis (read this first)

**This is NOT a "replace custom with Material3" job.** Every custom component here exists *because* bare M3
defaults don't match LiquidGlass (gradient/glass fills, sliding glass indicators, accent glow, frosted
surfaces). Swapping `SegmentedControl`→`SingleChoiceSegmentedButtonRow`, `GlassButton`→`Button`, or
`GlassCard`→`Card` would **regress the look**. The codebase already adopts M3 correctly where it fits
(`AppToggleCard` uses the native `Switch`; `EmptyState` uses `AnimatedVisibility`).

So the real wins are **consistency, de-duplication, and dead code** — not wholesale replacement.

## 2. Per-component verdict

| Component | LOC | Uses | Verdict | Why |
|---|---|---|---|---|
| **GlassCard** | 86 | 5 | ✅ KEEP | The structural glass primitive. Already migrated to `pressElevation`. Sound. |
| **GlassButton** | 147 | 3 | 🔧 **FIX** | Still hand-rolls the flat press-scale (`0.96f`, no token) that `GlassCard`/`ListRow` now get from the shared `pressElevation`. Plus a dead `BorderStroke(1.dp, Color.Transparent)` on Ghost. |
| **ListRow** | 122 | **0 — DEAD** | ♻️ **ADOPT or DELETE** | Fully built (leading disc, title/subtitle, trailing, `pressElevation`) but called nowhere — while 4 screens hand-roll the same row inline. |
| **SegmentedControl** | 122 | 1 | ✅ KEEP | Custom justified: the sliding *glass* indicator is the aesthetic. M3 `SegmentedButton` can't reproduce it. |
| **AppToggleCard** | 146 | 1 | 🔧 minor | Good (wraps GlassCard + native Switch). But `Icons.Filled.Message` is deprecated (the recurring build warning) → `Icons.AutoMirrored.Filled.Message`. `displayLabel()`/`glyph()` are domain mappings living in a UI file (consider moving). |
| **EmptyState** | 95 | 4 | ✅ KEEP | Clean; icon scales in via `motionScaleIn`. |
| **PillBadge** | 65 | 3 | ✅ KEEP (+opt) | Stateless tinted chip. Optional premium touch: `AnimatedContent` so count badges roll when they change. |
| **SectionHeader** | 60 | 3 | ✅ KEEP (+adopt) | Clean; should be used for the inline section titles screens currently hand-roll. |
| **LoadingShimmer / ShimmerBlock** | 76 | 2 | ✅ KEEP | Already on `compose-shimmer`, motion-gated. |

## 3. Recommended changes (prioritized — all preserve aesthetic/motion/blur)

**R1 — `GlassButton` → shared `pressElevation` (HIGH value · LOW risk · 1 file + 1 token)**
Replace the local `pressed`/`animateFloatAsState`/flat-scale `graphicsLayer` with
`.pressElevation(interaction, shape, pressedScale = Motion.PressScaleButton)`. Add
`val PressScaleButton = 0.96f` to the `Motion` object (kills the magic number). Delete the no-op
`BorderStroke(1.dp, Color.Transparent)` Ghost branch (it draws nothing). Net: buttons gain the same
depth+accent-shadow press as cards/rows → one consistent press language, less duplicated code.

**R2 — Resurrect `ListRow` + `SectionHeader` across inline-row screens (HIGH value · MED effort · MED risk)**
`AlertSettings` (7 inline `Row`s), `PrivacyLock` (10), `RuleEditor` (9), `Appearance` (6) hand-build rows
that `ListRow` already models (leading accent disc, title/subtitle, trailing slot, press feedback, 56dp
target). Adopting `ListRow`/`SectionHeader` removes ~30 duplicated row blocks and makes press feedback +
spacing uniform. **Aesthetic is identical** (ListRow uses the same tokens). *Risk:* touches several screens
the concurrent sessions are also editing — sequence carefully (see §5). **Alternative if the inline rows are
intentionally bespoke: delete `ListRow`** rather than leave dead code.

**R3 — `AppToggleCard` icon fix (TRIVIAL)** — `Icons.Filled.Message` → `Icons.AutoMirrored.Filled.Message`.
Removes the only recurring compile warning.

**R4 — `PillBadge` animated count (LOW · premium polish)** — wrap the label in `AnimatedContent` so vault
edited/deleted counts and match tallies roll over instead of snapping. Motion-gated.

**R5 — `SegmentedControl` indicator (LOW · cosmetic)** — the sliding indicator uses a flat
`primary.copy(alpha=0.20f)` fill; giving it the `glass()` treatment would make it read more "liquid,"
matching the bar it sits in.

## 4. Explicitly do NOT do
- Don't replace `SegmentedControl` / `GlassButton` / `GlassCard` with bare M3 `SegmentedButton` / `Button` /
  `Card` — you'd lose the gradient/glass/glow/sliding-indicator that *is* the brand.
- Don't add an external component library for any of this — everything above is first-party + the existing
  tokens. (Consistent with `LIBRARY_CATALOG.md`: the design is already served by hand-roll + native.)

## 5. Implementation status

**Implemented (component-local — chosen to avoid colliding with concurrent screen edits):**
- ✅ **R1** — `GlassButton` now uses the shared `pressElevation` (scale + accent lift shadow); added
  `Motion.PressScaleButton = 0.96f`; removed the no-op Ghost `BorderStroke(…Transparent)` and the dead
  `animateFloatAsState`/`collectIsPressedAsState`/`MotionTokens` imports. Press language now uniform across Card/Row/Button.
- ✅ **R3** — `AppToggleCard` `Icons.Filled.Message` → `Icons.AutoMirrored.Filled.Message`. Killed the last compile warning (build is now warning-free).
- ✅ **R4** — `PillBadge` rolls its label via `AnimatedContent` (vertical slide + fade) when a count changes; reduced motion renders it statically (instant).
- ✅ **R5** — `SegmentedControl` sliding indicator now uses the real `glass()` treatment (frosted pill + lit edge + faint accent wash) instead of a flat tint.
- **Verified:** `compileDebugKotlin` + `assembleDebug` + `testDebugUnitTest` all BUILD SUCCESSFUL (JDK17); zero warnings; no new deps; no INTERNET.

**Deferred — R2** (resurrect `ListRow`/`SectionHeader` across `AlertSettings`/`PrivacyLock`/`RuleEditor`/`Appearance`):
touches many screens the concurrent sessions are editing. Do it on a **settled tree** or it will collide (the
churn documented in `lessons.md`). Until then, `ListRow` remains dead code — adopt it (R2) or delete it.
