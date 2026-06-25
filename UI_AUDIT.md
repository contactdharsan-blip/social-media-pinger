# QuietPing — UI/UX Audit

> ## 2026-06-25 — Comprehensive re-audit + fix pass (CONVERGED CLEAN)
>
> The 2026-06-24 audit below (64/100) was re-run from scratch against current source with 4
> parallel auditors (navigation/state, accessibility, visual/theming, motion/perf), every finding
> implemented, then **re-audited twice until clean**. Net result: all CRITICAL/HIGH/MEDIUM findings
> closed; the app is navigable end-to-end, accessible, accent-faithful, and state-complete.
>
> **What changed (high level):**
> - **Navigation (was the gating problem):** Settings is now a real hub → Appearance / Privacy & lock /
>   **new About** screen reachable; Vault row→thread tap works (C3); existing rules are editable (H1);
>   onboarding shows **once** then persists (DataStore flag → conditional start destination).
> - **Privacy invariant:** the decoy-PIN session now hides real content on the **Home feed, media
>   gallery, and threads** too (previously only the Vault list honored `DecoyMode`).
> - **Accessibility (was 0 semantic annotations):** toggle cards/rows are single `Role.Switch` targets
>   with state announced; option chips are `Role.RadioButton`; segmented tabs `Role.Tab`; sub-48dp
>   targets floored; shimmer hidden + "Loading" announced; empty/error live-regions; break-in announced
>   assertively; `TextTertiary` bumped to clear WCAG AA on glass.
> - **Theming/consistency:** every hardcoded `Emerald400` chrome site → live `LocalQuietPingTheme.accent`
>   (custom accent now applies app-wide); one canonical app glyph map; new shared `AccentSwitch`,
>   `SettingToggleRow`, `ChoiceChip`, `AccentIconDisc`, `quietPingFieldColors`, `Spacing`/`TabularFigures`
>   tokens; HomeScreen/Onboarding/AppLock/Settings de-duplicated onto the shared library.
> - **State contract:** every data-Flow ViewModel has `.catch` + an `errorMessage` + a rendered error
>   branch; Home/Rules render loading skeletons.
> - **Motion/perf:** per-row infinite `travelingGlowBorder`/`sheen` removed from scrolling Vault rows
>   (kept as primitives for hero use); reduced-motion gates completed.
>
> **Verified:** `compileDebugKotlin` green/warning-free · `testDebugUnitTest` 100 tests, 0 fail ·
> `assembleDebug` 42MB APK · merged manifest 0 `INTERNET` (no-exfiltration invariant intact).
>
> **Deferred (LOW, documented, non-blocking):** VaultThread arg String-fallback (route is typed Long);
> a few literal `16.dp` gutters not yet swapped to the `Spacing` token; placeholder fonts (intentional);
> match-row → Vault list (MatchLog carries no `conversationId`). Remaining: a live-device smoke pass.
>
> The original 2026-06-24 audit is preserved below as the historical baseline.
>
> ---

**Scope:** Compose UI under `app/src/main/java/com/quietping/ui/` audited against PRD §6/§8/§9 and the documented DESIGN.md "Dark Liquid-Glass" system.
**Date:** 2026-06-24
**Overall UX score: 64 / 100** — Strong design-system foundations and high-quality individual screens, undercut by broken navigation wiring (two whole feature areas unreachable) and an incomplete loading/error contract.

**Issue counts:** Critical **3** · High **5** · Medium **6** · Low **6**

> Claims below were spot-verified against source. Note: a literal `DESIGN.md` file is **not** present in the repo (only `PRD.md`); fidelity is assessed against the DESIGN.md spec as referenced throughout the code/KDoc.

---

## 1. UX Scorecard

| Dimension | Score | Verdict |
|---|---:|---|
| Theme fidelity (vs DESIGN.md) | 82 | Faithful port; motion is excellent. Accent indirection broken in ~29 sites; no real blur/glow. |
| Screen completeness | 52 | All 11 screens exist & are non-stub, but 4 routes/flows are dead and 2 PRD items have no UI. |
| State handling (loading/empty/error) | 62 | Vault/Thread honor the 4-state contract; Home & Rules have **no** loading; **no** error leg anywhere. |
| Consistency & reuse | 58 | Good shared library, but parallel mini-libraries + 29 hardcoded accents break custom-accent. |
| **Overall** | **64** | Foundations strong; wiring + state contract are the gating problems. |

---

## 2. Prioritized Issues (critical & high first)

### CRITICAL

**C1 — Appearance screen is unreachable (dead route)**
`AppearanceScreen` is fully built and registered, but **nothing** calls `onNavigate(Dest.Appearance)`. The Settings bottom-nav tab opens `AlertSettingsScreen`, which has no link to it. An entire PRD §9.1 feature area (icon grid + theme + live preview) has zero entry point.
- `app/src/main/java/com/quietping/ui/nav/QuietPingNavGraph.kt:164` (route registered, never targeted)
- `app/src/main/java/com/quietping/ui/settings/AlertSettingsScreen.kt:82` (Settings root has no nav rows)
- **Fix:** Turn `AlertSettingsScreen` into a real Settings hub (or add a dedicated Settings root) with a `ListRow` calling `onNavigate(Dest.Appearance)`. Since `Dest.Appearance` falls through `navigateTo`'s else branch, a plain `onNavigate(Dest.Appearance)` call is sufficient.

**C2 — Privacy & lock screen is unreachable (dead route)**
`PrivacyLockScreen` (purge / retention / biometric-lock toggle) is registered but nothing calls `onNavigate(Dest.PrivacyLock)`. Users can never reach purge-now, retention window, or enable the AppLock gate from inside the app — which also makes the biometric gate impossible to turn on via UI.
- `app/src/main/java/com/quietping/ui/nav/QuietPingNavGraph.kt:167` (route registered, never targeted)
- **Fix:** Add a "Privacy & lock" entry to the Settings hub calling `onNavigate(Dest.PrivacyLock)` (same hub fix as C1).

**C3 — Vault conversation → thread tap is a no-op (core §6D flow broken)**
The nav graph calls `VaultScreen(onNavigate = navigate, onBack = back)` and never passes `onOpenThread`, so it stays the empty-lambda default. Tapping any conversation row does nothing; `VaultThreadScreen` (edited history / deleted "Recovered") is unreachable in normal use. `navigateToThread()` exists but has no call site.
- `app/src/main/java/com/quietping/ui/nav/QuietPingNavGraph.kt:126` (invoked without `onOpenThread`)
- `app/src/main/java/com/quietping/ui/vault/VaultScreen.kt:89` (`onOpenThread` defaults to `{}`)
- `app/src/main/java/com/quietping/ui/nav/QuietPingNavGraph.kt:195` (`navigateToThread` defined, never called)
- **Fix:** `VaultScreen(onNavigate = navigate, onBack = back, onOpenThread = { id -> navController.navigateToThread(id) })` — exactly what VaultScreen's KDoc already documents.

### HIGH

**H1 — Editing an existing rule is impossible**
Every rule card wires `onClick = { onNavigate(Dest.RuleEditor) }`, and the nav graph maps `is Dest.RuleEditor -> navigateToRuleEditor(null)` (always `NEW_RULE_ID`). Tapping an existing rule always opens a blank new-rule editor; the rule id is never passed. `RuleEditorViewModel` fully supports editing by id. Violates PRD §9.1 (rule list → rule editor).
- `app/src/main/java/com/quietping/ui/rules/RulesScreen.kt:120` (`onClick = { onNavigate(Dest.RuleEditor) }`)
- `app/src/main/java/com/quietping/ui/nav/QuietPingNavGraph.kt:184` (`navigateToRuleEditor(null)`)
- **Fix:** Add `onEdit:(Long)->Unit` to `RulesScreen`, set `RuleCard onClick = { onEdit(rule.id) }`, and wire `RulesScreen(... onEdit = { id -> navController.navigateToRuleEditor(id) })`. Keep header/empty-state "New" buttons on the null path.

**H2 — Home match-feed renders no loading state**
During initial Flow collection (`isLoading = true`) the apps list is empty and `isFeedEmpty` is gated false, so only two static section labels show over blank space — the classic "blank/jank while Flows collect" failure.
- `app/src/main/java/com/quietping/ui/home/HomeScreen.kt:93` (items over empty `uiState.apps`; only branches feed-empty vs content)
- `app/src/main/java/com/quietping/ui/home/HomeUiState.kt:54` (`isFeedEmpty = !isLoading && recentMatches.isEmpty()`)
- **Fix:** Add an explicit `if (uiState.isLoading)` arm emitting shimmer placeholders (reuse `LoadingShimmer`/`GlassCard` like `VaultLoading`) for the apps area + a skeleton under "Recent matches", then fall through to data/empty.

**H3 — Rules screen renders no loading state**
The body only branches `state.isEmpty` (false while loading) vs `state.groups` (empty while loading), so nothing renders under the header until the first emission.
- `app/src/main/java/com/quietping/ui/rules/RulesScreen.kt:92`
- `app/src/main/java/com/quietping/ui/rules/RulesUiState.kt:32` (`isEmpty = !isLoading && groups.isEmpty()`)
- **Fix:** Insert `if (state.isLoading) { items(5){ GlassCard{ LoadingShimmer(lines=2) } } }` before the isEmpty/content branches, mirroring `VaultScreen`.

**H4 — Hardcoded `Emerald400` breaks the user-customizable accent (~29 sites / 8 screens)**
The theme exposes a custom accent (`LocalQuietPingTheme.current.accent`, set from `settings.accentHex`), and Vault/Rules cards use it correctly — but accent-colored chrome (switch tracks, slider thumbs, chip fills, icon discs, progress dots) is painted with the literal `Emerald400`. Pick any non-emerald accent and Home, all three Settings screens, Lock, Onboarding, and parts of Rules **stay green**. Proven inconsistency: identical retention chip uses dynamic accent at `RuleEditorScreen.kt:329` but fixed `Emerald400` at `PrivacyLockScreen.kt:201` / `AlertSettingsScreen.kt:254`. (32 total `Emerald400` refs; 2 are legitimate in `theme/Color.kt` + `theme/Theme.kt`.)
- `app/src/main/java/com/quietping/ui/home/HomeScreen.kt:172`, `:225`
- `app/src/main/java/com/quietping/ui/settings/AlertSettingsScreen.kt:135`, `:157`, `:254`, `:461`
- `app/src/main/java/com/quietping/ui/settings/AppearanceScreen.kt:331` (its **own** intensity slider ignores the chosen accent)
- `app/src/main/java/com/quietping/ui/settings/PrivacyLockScreen.kt:201`, `:309`
- `app/src/main/java/com/quietping/ui/lock/AppLockScreen.kt:149`; `app/src/main/java/com/quietping/ui/onboarding/OnboardingScreen.kt:326`; `app/src/main/java/com/quietping/ui/rules/RuleEditorScreen.kt:401`; `app/src/main/java/com/quietping/ui/rules/RulesScreen.kt:220`
- **Fix:** Read `val accent = LocalQuietPingTheme.current.accent` (or `MaterialTheme.colorScheme.primary`) once per composable and use it for switch `checkedTrackColor`, icon tints, slider thumb/track, and selected fills/borders. Reserve `Emerald*` for the `parseHexColor` fallback only. Single highest-leverage fix — restores accent fidelity across all 8 screens.

**H5 — HomeScreen re-implements four shared components (visual drift)**
Private `AppToggleCard` (raw `Switch` + hardcoded colors), `IconBadge`, `FeedEmptyState`, and `SectionLabel` duplicate the shared `components/AppToggleCard`, accent disc, `EmptyState`, and `SectionHeader`. It also redefines `AppPackage.icon()` with glyphs that **conflict** with the shared `AppPackage.glyph()` (Messenger = `Message` here vs `Forum` shared).
- `app/src/main/java/com/quietping/ui/home/HomeScreen.kt:156` (private `AppToggleCard`) vs `components/AppToggleCard.kt:71`
- `app/src/main/java/com/quietping/ui/home/HomeScreen.kt:266` (`FeedEmptyState`) vs `components/EmptyState.kt:36`
- **Fix:** Delete the private duplicates; call shared `AppToggleCard`/`EmptyState`/`SectionHeader` and use shared `AppPackage.glyph()`/`displayLabel()`.

---

## 3. Screen-Map Completeness Check

### Present & correctly wired (11 screens exist, non-stub)
| Screen | Entry | Status |
|---|---|---|
| Onboarding | NavHost start | OK — stepped Notification→SMS→DND→Accessibility, skippable |
| AppLock | `MainActivity.AppLockGate` | OK — biometric gate, degrades gracefully (nav `Dest.AppLock` route is dead but the real gate is outside the graph) |
| Home/Dashboard | bottom-nav root | OK — toggle cards + match feed (match history correctly lives in feed, not a tab) |
| Vault (list) | bottom-nav root | OK — All/Deleted/Edited filters + search present |
| Vault Thread | via `navigateToThread` | Built & strong (word-diff history, "Recovered") — **but unreachable**, see C3 |
| Rules | bottom-nav root | OK list; **edit path broken**, see H1 |
| Rule Editor | param route | OK (KeywordEditor + VipPicker); only reachable as NEW, see H1 |
| Alert Settings | bottom-nav "Settings" | OK — sound presets / vibration / DND |
| Appearance | — | Built & strong — **UNREACHABLE**, see C1 |
| Privacy & Lock | — | Built & strong — **UNREACHABLE**, see C2 |

### Unreachable (built but no entry point)
- **Appearance** (`appearance`) — C1
- **Privacy & Lock** (`privacy_lock`) — C2
- **Vault Thread** (`vault_thread/{id}`) — reachable only if C3 is fixed
- **Rule Editor (edit mode)** — only NEW reachable until H1 is fixed
- `app_lock` route — dead by design (gate lives in `MainActivity`); not user-facing, no action needed

### Missing (PRD requires, no UI exists)
| Missing item | PRD ref | Severity | Note |
|---|---|---|---|
| **Settings hub** (parent for Alert/Appearance/Privacy/About) | §9.1 | Critical (root cause of C1/C2) | Bottom-nav Settings opens AlertSettings directly; no landing screen |
| **About / permissions status** screen | §9.1 | Medium | No way to review live permission grants post-onboarding, or version info |
| **Match detail → jump to source / Vault thread** | §9.1 | Medium | `MatchRow onClick = { onNavigate(Dest.Vault) }` jumps to the bare list; code comment notes `MatchLog` lacks a `conversationId` to resolve (`HomeScreen.kt:110`) |
| Vault filters (per-app/deleted/edited/search) | §6D/§9.1 | — | **Actually present** in VaultScreen (initial inventory flagged as unverified; confirmed implemented) |

---

## 4. Theme-Fidelity Verdict (vs DESIGN.md) — 82/100

**Faithful and well-documented.** `Color.kt` is a near-1:1 port of the emerald/teal ramps, near-black canvas `#030712`, raised surfaces, semantic + glass + border tokens (exact hex + alpha). `GlassDefaults` maps radii (24/16/12/9999dp = `--radius-2xl/lg/md/full`) exactly. **Motion is the standout:** `MotionTokens.signatureSpring()` (dampingRatio 0.55) genuinely overshoots like the spec's `cubic-bezier(0.34,1.56,0.64,1)`, applied consistently (press scale, segmented indicator, nav/expand transitions) and degrades to snap/gentle when the motion gate is off. No wrong radii, no stray hardcoded hex in screens.

**Gaps (none critical):**
- **M1 — No real backdrop blur** (the defining "liquid glass" trait). `Modifier.glass` applies only a translucent fill + 1px border; no `Modifier.blur`/`RenderEffect`/haze anywhere. Honestly documented in `app/src/main/java/com/quietping/ui/theme/Glass.kt:25`. **Fix:** on API 31+ apply `Modifier.blur(16.dp)` or `graphicsLayer { renderEffect = BlurEffect(...) }` to the ambient layer behind glass, gated by `glassIntensity`; keep fill+border as pre-31 fallback. *(medium)*
- **M2 — Accent indirection bypassed** — same as **H4** (the one fidelity gap worth fixing broadly). *(medium)*
- **L1 — No top-highlight "lit edge" / press glow** on glass (`Glass.kt:43`). DESIGN.md §7.1 keeps an inset top highlight + accent hover glow; current border is uniform. **Fix:** add a 1px top-edge vertical-gradient highlight + accent-tinted press glow on interactive `GlassCard`. *(low)*
- **L2 — No ambient radial-glow background** (`HomeScreen.kt:80` paints flat `colorScheme.background`). DESIGN.md §6 wants low-alpha drifting radial glows. **Fix:** reusable ambient-background modifier (accent ~0.08 / secondary ~0.06), optional slow drift gated by `motionEnabled`. *(low)*
- **L3 — Fonts unimplemented** — `DisplayFontFamily`/`BodyFontFamily` both = `FontFamily.Default` (`Type.kt:18`); Bricolage Grotesque / General Sans not bundled, and no `tabular-nums`/mono style for metric values. Indirection is already in place. **Fix:** bundle the families later; meanwhile add a `FontFamily.Monospace` style + `tabular-nums` feature setting so changing counts don't jitter. *(low)*

---

## 5. State-Handling & Consistency (remaining mediums/lows)

**M3 — No error state on any data-driven feed.** `.catch` count across `ui/` = **0**; no `UiState` has an error variant. A throw from a Room/SQLCipher Flow propagates through `combine/stateIn` and cancels `viewModelScope`, stranding the UI on `initialValue` — permanent shimmer (Vault/Thread) or permanent blank (Home/Rules), no message, no retry.
- `app/src/main/java/com/quietping/ui/vault/VaultViewModel.kt:42`, `home/HomeViewModel.kt:43`, `rules/RulesViewModel.kt:26`, `vault/VaultThreadViewModel.kt:64`
- **Fix:** add `errorMessage: String? = null` to each `UiState` and `.catch { emit(currentState.copy(isLoading=false, errorMessage=...)) }` before `stateIn`; add an error branch reusing `EmptyState` + retry. *(medium)*

**M4 — Parallel Settings mini-library.** `SettingsGlassCard`/`StatusPill`/`SectionLabel`/`GlassSwitch` (in `AlertSettingsScreen.kt`, reused by Appearance/PrivacyLock) duplicate `GlassCard`/`PillBadge`/`SectionHeader`. **Fix:** back them with the shared components; promote `GlassSwitch` to `ui/components`. *(medium)*

**M5 — Onboarding forks its own buttons** (comment: "not bound to the parallel-authored ui.components package"). `PrimaryGlassButton`/`GhostGlassButton` reproduce `GlassButton` but lose spring-press + motion-gate and use a 56.dp height vs the shared 48.dp target (`OnboardingScreen.kt:490`). **Fix:** replace with `GlassButton(style = …)`. *(medium)*

**M6 — Duplicated Switch color block ×4 + 7 raw `Switch` with 3 different treatments** (themed, hardcoded-Emerald, and default-colored at `HomeScreen.kt:191`). **Fix:** one shared `QuietPingSwitch` reading the theme accent; replace all 7. *(medium)*

**L4 — 7 near-identical "selectable chip"/swatch composables** (`SelectChip`/`PresetChip`/`RetentionChip` byte-identical, `KeywordChip`, etc.) with subtly different padding/radius/selection. **Fix:** shared `SelectableChip(text, selected, onClick, leadingIcon)`. *(low)*

**L5 — Accent icon-disc hand-rolled ×6** with mismatched sizes/alphas (44/40/36/28dp, 0.12–0.18 alpha) and one hardcoded `Emerald400` (`AlertSettingsScreen.kt:125`). **Fix:** extract `IconDisc(icon, size = 40.dp, tint = accent)`. *(low)*

**L6 — Untokenized spacing + duplicated text-field colors.** Home root uses `horizontal = 20.dp` vs `16.dp` everywhere else; `GlassCard` default `contentPadding` 16.dp vs 14.dp overrides; `OutlinedTextField` color block duplicated ×3. **Fix:** add a `Spacing`/`Dimens` token object + `quietPingTextFieldColors()`. *(low)*

---

## 6. Bottom Line

QuietPing has a genuinely strong foundation — a faithful Dark Liquid-Glass theme (motion especially nails the spec), a real shared component library, and eleven non-stub screens that are individually high-quality (Vault filters + word-diff history, a full rule editor, a live-preview Appearance screen, a working biometric gate). The problem is the wiring and contract layer that sits on top of it: two entire PRD feature areas (Appearance, Privacy & lock) are completely unreachable because nobody calls their nav destinations, the Vault list→thread tap is a silent no-op, you can't edit an existing rule, there's no Settings hub or About/permissions screen the PRD requires, Home and Rules flash blank instead of skeletons while data loads, and no feed has any error handling so a single DB failure strands the UI forever. None of this needs new design work — it's a handful of nav-graph parameter passes, two-or-three loading/error branches, and swapping ~29 hardcoded `Emerald400` references for the existing theme accent. Fix the three critical nav gaps plus the accent indirection and this jumps from "beautiful but half-wired" to genuinely shippable.
