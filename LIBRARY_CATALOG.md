# Kotlin / Compose Component & Effect Library Catalog — QuietPing

> **Status:** research + design-mapping. **No dependency has been added.** Per the agreed scope,
> this document catalogs the landscape, flags each library against QuietPing's hard invariants,
> maps the invariant-safe + design-fitting ones to the LiquidGlass system, and ends with a
> recommendation. Adding anything to `gradle/libs.versions.toml` is a separate, explicitly-approved step.

---

## 0. Source of truth note

`CLAUDE.md` references a `DESIGN.md`, but **no `DESIGN.md` exists in the repo** (searched whole tree).
The de-facto design specification is the code under `app/src/main/java/com/quietping/ui/theme/`
(`Color.kt`, `Glass.kt`, `GlassEffects.kt`, `Motion.kt`, `Theme.kt`, `Type.kt`). The section-number
references in those KDocs (e.g. "DESIGN.md §2.2") point at a spec that lives only as inline values now.
**This catalog treats `ui/theme/` as the authoritative design system.**

---

## 1. The invariant filter (every library is judged through this lens)

QuietPing is a zero-network, on-device privacy app. A library is **Disqualified** the moment it
violates one of these, no matter how good it looks:

| # | Invariant | Library consequence |
|---|-----------|---------------------|
| 1 | **No `INTERNET` permission, no networking dependency** | Anything pulling okhttp / retrofit / ktor / a CDN, or requiring INTERNET, is OUT. This kills most image loaders and anything with telemetry. |
| 2 | **Fully on-device — no analytics / telemetry / crash-reporting** | Any lib that phones home is OUT. |
| 3 | **Encrypted at rest; nothing writes message content to plain storage/logs** | A lib that caches content to disk unencrypted is OUT for content; fine for chrome-only use. |
| 4 | **Reduced-motion contract** — all motion gated by `LocalQuietPingTheme.motionEnabled` | A lib whose animation can't be disabled/short-circuited cannot wrap message content; usable only where motion is acceptable. |
| 5 | **`minSdk 26`** | Lib must support API 26 (or degrade gracefully — note where a real effect needs API 31+ RenderEffect). |
| 6 | **Compose BOM 2024.10.01 / Kotlin 2.0.21 / KSP** | Lib must be Compose-compatible at these versions; no kapt-only blockers. |
| 7 | **Simplicity-first / surgical (CLAUDE.md)** | "If 200 lines could be 50, write 50." A lib must beat the hand-roll on real value, not just add surface. Several effects are already hand-rolled well — duplicating them is a regression. |

**License gate:** prefer Apache-2.0 / MIT; flag anything copyleft (GPL/LGPL) for an app headed to Play.

---

## 2. The LiquidGlass design system as it exists in code

What a library has to fit *into* (and not fight):

- **Palette (`Color.kt`):** near-black canvas `#030712`, raised grays, **emerald→teal** accent ramp
  (`Emerald400 #34D399` primary, `Teal500 #14B8A6` secondary), semantic statuses (success/warning/error/alert),
  glass fill `white@0.07`, glass border `white@0.14`.
- **Glass surface (`Glass.kt` · `Modifier.glass(intensity, cornerRadius)`):** translucent white fill +
  1px lit border + rounded corners (24/16/12dp + pill). **KDoc explicitly says the blur is faked** —
  real backdrop blur isn't reachable from a single modifier; call sites are told to add `Modifier.blur`
  to the *background* layer on API 31+. Radius tokens: `2xl 24`, `lg 16`, `md 12`, `full`.
- **Animated embellishments (`GlassEffects.kt`):**
  - `Modifier.travelingGlowBorder()` — a sweep-gradient lobe orbiting the rounded-rect edge
    (offscreen `saveLayer` + `BlendMode.Clear` punch). Emerald→teal ramp, ~3.2s/orbit. Reduced-motion → static edge.
  - `Modifier.sheen()` — slow diagonal highlight sweep. Reduced-motion → no-op.
- **Motion (`Motion.kt` + `MotionTokens` in `Glass.kt`):** spring vocabulary (`signatureSpring` overshoot,
  `modalSpring`, `gentleSpring`, `offsetSpring`); reduced-motion-aware helpers (`animatedItem`,
  `animateSizeChange`, `motionEnter/Exit`, `motionScaleIn`). **Single motion gate**, read from theme.
- **Typography (`Type.kt`):** full M3 type scale wired, **but display/body fonts are placeholders**
  (`FontFamily.Default`) because no font files are bundled. Aliases `DisplayFontFamily` / `BodyFontFamily`
  exist precisely so real fonts can be dropped in one place.

---

## 3. Concrete design needs a library could actually serve

Grounded in the code above + `UI_AUDIT.md` + `tasks/todo.md` "findings to address later". This is where
"apply based on design" has real teeth — these are gaps the design *wants* closed:

| Need | Evidence in repo | Candidate category | Invariant notes |
|------|------------------|--------------------|-----------------|
| **Real backdrop blur** for glass | `Glass.kt` KDoc admits blur is faked | Blur / frosted-glass lib (Haze) | Pure GPU; API 31+ for true blur, must degrade ≤30 |
| **Loading / skeleton states** | `UI_AUDIT.md`, todo "Home/Rules no loading state" | Shimmer / skeleton | Must be motion-gateable |
| **Local media display** in vault | todo "UI surfacing of captured media = follow-up"; `MediaVault` copies to private `filesDir` | Zoomable local-image viewer (Telephoto) | MUST load from local `File`/`Uri` only — no network loader |
| **Real display/body fonts** | `Type.kt` placeholders | Bundled OFL font *resource* (not a lib) | Bundle the file; never use downloadable Google Fonts (network) |
| Reorderable rule/VIP lists *(maybe)* | Rules/VipPicker lists | Drag-reorder lib | Pure UI; low priority |
| Charts / data-viz | — none found | — | **No real need** — QuietPing has no dashboards; do not add a chart lib speculatively |

---

## 4. Library landscape

Populated from two parallel research passes, version- and maintenance-verified via WebSearch (mid-2026).
Each row carries the network/privacy verdict that drives §1. Ordered within each category by relevance to
this app. **"Already available"** = ships in the current Compose BOM 2024.10.01 → zero new dependency.

### 4a. Component / UI building-block libraries

#### Image / media display — highest relevance (media vault renders local images)

| Library | Maintainer | Latest | Maintained? | License | minSdk ≤26 | Network/INTERNET? | Purpose | Relevance |
|---|---|---|---|---|---|---|---|---|
| **Telephoto** `me.saket.telephoto:zoomable-android` | saket | 0.18.0 | Active | Apache-2.0 | Yes (21) | **None** — base `zoomable`/`zoomable-android` are pure gesture+subsampling. Only the `zoomable-image-coil*`/`-glide` *bundles* inherit a loader's network → use the base module + a local painter. | Pan/zoom + subsampling for large local images | **High** — vault full-screen image viewer |
| **Coil 3** `io.coil-kt.coil3:coil-compose` | coil-kt | 3.5.0 | Active (leading) | Apache-2.0 | Yes (21) | **None in core (v3):** `coil-compose` alone has no network dep; network is opt-in via separate `coil-network-okhttp`/`-ktor`. Add ONLY `coil-compose` + `content://`/`File` fetchers → zero `INTERNET`. | Async local image loading/caching | **High** — load vault `content://`/file images; omit `coil-network-*` |
| **Landscapist** `com.github.skydoves:landscapist-coil3` | skydoves | 2.5.1 | Active | Apache-2.0 | Yes (21) | Loader core drags okhttp/ktor via its Coil3/Glide backend → flag. | Pluggable image loading + state plugins | **Low** — brings a loader you don't need |
| **Glide Compose** `com.github.bumptech.glide:compose` | bumptech | 1.0.0-beta09 | Partial (perpetual beta) | Apache-2.0 | Yes (14) | Glide core bundles HTTP; network-oriented → needs `INTERNET` in practice. | Async image loading | **Disqualified** — network + unstable |
| **Compose ImageLoader** `io.github.qdsfdhvh:image-loader` | qdsfdhvh | ~1.10.x | Active (niche) | Apache-2.0 | Yes | ktor-based by default → pulls ktor. | KMP image loading | **Disqualified** — ktor by default |

#### Skeleton / loading / shimmer — known gap: no loading states

| Library | Maintainer | Latest | Maintained? | License | minSdk ≤26 | Network/INTERNET? | Purpose | Relevance |
|---|---|---|---|---|---|---|---|---|
| **compose-shimmer** `com.valentinilk.shimmer:compose-shimmer` | valentinilk | 1.4.0 | Active | MIT | Yes | **None** — pure-Compose draw modifier, zero transitive deps. | `Modifier.shimmer()` skeleton | **High** — drop-in skeletons for Home/Rules/Vault |
| **compose-shimmer-skeleton** `io.github.timoseyfarth…` | timoseyfarth | ~1.x | Active (small) | MIT/Apache | Yes | **None** | Shimmer + ready-made skeleton shapes | **Med** — if you want pre-built skeletons |
| **Lottie Compose** `com.airbnb.android:lottie-compose` | airbnb | 6.7.1 | Active | Apache-2.0 | Yes (21) | **None for bundled local assets** (`res/raw`); `INTERNET` only on the `.Url` spec you don't use. | After-Effects JSON animation | **Med** — branded loader / empty-state art, local JSON only |
| **accompanist-placeholder-material3** | google | 0.36.0 | **Deprecated** | Apache-2.0 | Yes | None | Placeholder/shimmer | **Disqualified (EOL)** — use compose-shimmer |

#### Lists & reordering — known gap: drag-reorder

| Library | Maintainer | Latest | Maintained? | License | minSdk ≤26 | Network? | Purpose | Relevance |
|---|---|---|---|---|---|---|---|---|
| **Reorderable** `sh.calvin.reorderable:reorderable` | Calvin-LL | 3.1.0 | Active (modern `animateItem`) | Apache-2.0 | Yes | **None** — pure Compose, zero transitive deps | Drag-reorder Lazy lists/grids/Column/Row | **High** — reorder Rules / VIP / keyword priority |
| **ComposeReorderable** `org.burnoutcrew…` | aclassen | 0.9.6 | Stale (superseded) | Apache-2.0 | Yes | None | Older drag-reorder | **Low** — prefer Calvin-LL |
| **SwipeToDismissBox** | Material3 | in BOM | First-party | Apache-2.0 | Yes | None | Swipe-to-dismiss rows | **Already available** — no dep |
| **`Modifier.animateItem()`** | foundation | in BOM | First-party | Apache-2.0 | Yes | None | Item add/remove/move animation | **Already available** — app already uses it |

#### Charts / data-viz — only if a stats/digest screen is added (no current need)

| Library | Maintainer | Latest | Maintained? | License | minSdk ≤26 | Network? | Purpose | Relevance |
|---|---|---|---|---|---|---|---|---|
| **Vico** `com.patrykandpatrick.vico:compose-m3` | patrykandpatrick | 3.0.1 stable | Very active | Apache-2.0 | Yes (21) | **None** — pure Canvas; `compose-m3` themes to Material3 | Line/bar/column charts | **Med** — best fit *if* a digest screen lands |
| **ComposeCharts** `io.github.ehsannarmani:compose-charts` | ehsannarmani | ~0.x | Active | Apache-2.0 | Yes | None | Animated line/bar/pie | **Med** — simpler animated alt |
| **YCharts / KoalaPlot / JetChart** | various | — | mixed | Apache/MIT | Yes | None | Charting | **Low** — Vico preferred |
| **MPAndroidChart** `com.github.PhilJay` | PhilJay | 3.1.0 | **Frozen** | Apache-2.0 | Yes | None (View-based) | Classic View charts | **Low** — legacy, awkward in Compose |

#### Calendar / date

| Library | Maintainer | Latest | Maintained? | License | minSdk ≤26 | Network? | Purpose | Relevance |
|---|---|---|---|---|---|---|---|---|
| **Kizitonwose Calendar** `com.kizitonwose.calendar:compose` | kizitonwose | 2.9.0 | Active (de-facto standard) | MIT | Yes (21) | **None** — `java.time` | Month/week calendar | **Low** — no calendar surface today; top pick *if* one appears |
| **Material3 DatePicker / DateRangePicker** | Material3 | in BOM | First-party | Apache-2.0 | Yes | None | Date/range dialogs | **Already available** — covers retention/digest dates |

#### Dialogs / sheets / tooltips / settings

| Library | Maintainer | Latest | Maintained? | License | minSdk ≤26 | Network? | Purpose | Relevance |
|---|---|---|---|---|---|---|---|---|
| **Compose-Settings** `com.github.alorma.compose-settings:ui-m3` | alorma | ~2.16.0 | Active | Apache-2.0 | Yes | **None** — storage is local DataStore/prefs | Pre-built M3 settings rows/switches | **Med** — trims boilerplate on AlertSettings/Appearance/Privacy |
| **TooltipBox / ModalBottomSheet / BasicAlertDialog** | Material3 | in BOM | First-party | Apache-2.0 | Yes | None | Tooltips, sheets, dialogs | **Already available** — prefer over 3rd-party |
| **Balloon** `com.github.skydoves:balloon-compose` | skydoves | 1.7.6 | Active | Apache-2.0 | Yes (21) | **None** | Styled arrow tooltips/popups | **Low** — only if M3 TooltipBox insufficient |
| **Sheets-Compose-Dialogs** `com.maxkeppeler…` | maxkeppeler | ~1.3.0 | Slowing | Apache-2.0 | Yes | None | Modular dialogs/sheets | **Low** — M3 now covers most |
| **compose-color-picker** `com.godaddy.android.colorpicker` | godaddy | 0.7.0 | Stale-ish | MIT | Yes | None | HSV color picker | **Low** — only for richer accent picking |

#### Layout / adaptive / navigation

| Library | Maintainer | Latest | Maintained? | License | minSdk ≤26 | Network? | Purpose | Relevance |
|---|---|---|---|---|---|---|---|---|
| **navigation-compose** `androidx.navigation` | Google | 2.8.x | First-party (in app) | Apache-2.0 | Yes | None | Nav graph | **Already in use** — keep; no 3rd-party nav warranted |
| **Material3 Adaptive** `androidx.compose.material3.adaptive` | Google | 1.3.0-beta02 | First-party | Apache-2.0 | Yes | None | ListDetailPaneScaffold, window-size class | **Low** — phone-first; shelf for tablet later |
| **`FlowRow`/`FlowColumn`, `HorizontalPager`** | foundation | in BOM | First-party | Apache-2.0 | Yes | None | Flow layout / pager | **Already available** — use for keyword chips/onboarding |
| **Voyager / Decompose / Compose Destinations** | various | — | active | MIT/Apache | Yes | None | Alt navigation paradigms | **Low / arch-conflict** — you use Hilt + nav-compose; don't mix |

#### Form / input

| Library | Maintainer | Latest | Maintained? | License | minSdk ≤26 | Network? | Purpose | Relevance |
|---|---|---|---|---|---|---|---|---|
| **BasicTextField2 / `TextFieldState`** | foundation | in BOM | First-party | Apache-2.0 | Yes | None | State-based text input | **Already available** — keyword/VIP entry |
| **compose-rich-editor** `com.mohamedrejeb.richeditor` | MohamedRejeb | 1.0.0-rc14 | Active | Apache-2.0 | Yes | **None** — local HTML/MD parse | WYSIWYG rich text | **Low** — no rich-text surface here |
| **multiplatform-markdown-renderer** `com.mikepenz` | mikepenz | 0.39.2 | Active | Apache-2.0 | Yes | Core network-free; skip the optional Coil image plugin | Render Markdown to Compose | **Low** — only for in-app help/changelog |

#### Permissions & utility (Accompanist successors)

| Library | Maintainer | Latest | Maintained? | License | minSdk ≤26 | Network? | Purpose | Relevance |
|---|---|---|---|---|---|---|---|---|
| **accompanist-permissions** `com.google.accompanist:accompanist-permissions` | google | 0.37.3 | **Last surviving module** (on borrowed time) | Apache-2.0 | Yes | **None** | `rememberPermissionState` for runtime grants | **High** — SMS / POST_NOTIFICATIONS / media grants; plan to inline eventually |
| **accompanist-drawablepainter** | google | 0.36.0 | Minimal | Apache-2.0 | Yes | None | `Drawable`→`Painter` | **Low** — legacy interop only |

#### Deprecated / absorbed into platform — do NOT add as new deps

| Was | Now (first-party replacement, already in BOM) |
|---|---|
| accompanist-systemuicontroller | `Activity.enableEdgeToEdge()` *(app already uses)* |
| accompanist-pager / -pager-indicators | `HorizontalPager` / `VerticalPager` |
| accompanist-flowlayout | `FlowRow` / `FlowColumn` |
| accompanist-swiperefresh | `PullToRefreshBox` |
| accompanist-navigation-animation / -material | native nav animations *(app already does)* / bottom-sheet |
| accompanist-placeholder | compose-shimmer |
| `Modifier.animateItemPlacement()` | `Modifier.animateItem()` *(app already migrated)* |
| MPAndroidChart / old ComposeReorderable | Vico / Calvin-LL Reorderable |

### 4b. Effect / animation / visual libraries (LiquidGlass)

Extra columns here: **Reduced-motion?** (can the effect be disabled to honor the gate) and **vs native/hand-roll**
(does it genuinely replace the team's `drawWithCache`/`saveLayer`/AGSL code, or just duplicate Compose/Android?).

#### Blur / frosted-glass / glassmorphism — the design centerpiece

| Library | Latest | Maintained? | License | Network? | Reduced-motion? | Purpose | Relevance | vs native/hand-roll |
|---|---|---|---|---|---|---|---|---|
| **Haze** `dev.chrisbanes.haze:haze` (+ `:haze-materials`) | 1.7.2 stable (2026-02); 2.0.0-alpha03 | Very active | Apache-2.0 | **None** | **Yes** — `enabled`/blur flag; falls back to scrim | True **backdrop** blur of content *behind* a composable | **High** | **Replaces** hand-rolled `saveLayer`/AGSL backdrop blur |
| **Native `Modifier.blur` + `RenderEffect`** | in BOM | Native | Apache-2.0 | None | Yes | Foreground blur of a composable's *own* pixels | **High (baseline)** | Native; **can't blur behind** — that's why Haze exists |
| **skydoves Cloudy** `com.github.skydoves:cloudy` | 0.6.0 (2026-06) | Active | Apache-2.0 | None | Yes | All-version blur (+CPU fallback) + "liquid glass" | **Med** | Mostly duplicates native foreground blur; not backdrop; no ring/sheen |
| **liquid-glass-android** `Mortd3kay` | 0.1.0-alpha (2025-09) | Stale | NOASSERTION (⚠ unclear) | None | Yes | Apple-style liquid glass via AGSL | **Low** | Duplicates your own AGSL; unclear license; no Maven artifact |
| `haze-jetpack-compose` (legacy coord) | 0.7.0 | No | Apache-2.0 | No | — | Old pre-1.0 Haze name | **Disqualified** | Superseded by `dev.chrisbanes.haze:haze` |

#### Vector / Lottie / Rive playback

| Library | Latest | Maintained? | License | Network? | Reduced-motion? | Purpose | Relevance | vs native/hand-roll |
|---|---|---|---|---|---|---|---|---|
| **Native `animation-graphics` `rememberAnimatedVectorPainter`** | in BOM | Native | Apache-2.0 | None | Yes (`atEnd` flag) | Two-state animated vector (icon morphs) | **High** | Native; prefer for quiet micro-interactions |
| **Lottie Compose** `com.airbnb.android:lottie-compose` | 6.7.1 | Active | Apache-2.0 | **None for `RawRes`/`Asset`** (only `.Url` needs net — don't call it) | Yes (`progress`/`isPlaying`) | After-Effects JSON player | **Med** | Adds capability native lacks (designed comps), local JSON only |
| **Compottie** `io.github.alexzhirkevich:compottie` | 2.2.4 | Very active | MIT | None (bundled) | Yes | Pure-Kotlin Lottie player | **Med** | Lighter Lottie alt |
| **Rive** `app.rive:rive-android` | 11.7.0 | Very active | MIT | None (bundled `.riv`) | Yes | Interactive vector + state machines | **Med** | Heavier; bundles native renderer; overkill for quiet app |
| **Kottie / Rive-CMP** | — | active/new | Apache-2.0 | None (bundled) | Yes | KMP wrappers | **Low** | Redundant for Android-only |

#### Shimmer / skeleton / loading — closes the "no loading states" gap

| Library | Latest | Maintained? | License | Network? | Reduced-motion? | Purpose | Relevance | vs native/hand-roll |
|---|---|---|---|---|---|---|---|---|
| **compose-placeholder** `com.eygraber:compose-placeholder[-material3]` | 1.0.12 | Active | Apache-2.0 | **None** | Yes (`visible=`, `highlight=null` → static) | Skeleton + optional shimmer/fade | **High** | Maintained successor to deprecated Accompanist placeholder |
| **compose-shimmer** `com.valentinilk.shimmer:compose-shimmer` | 1.4.0 | Active | MIT | **None** (only foundation+stdlib) | Yes (omit modifier) | `Modifier.shimmer()` | **High** | No native equivalent; tiny |
| **accompanist-placeholder** | 0.36.0 | **Deprecated** | Apache-2.0 | None | Yes | Skeleton/shimmer | **Disqualified (EOL)** | → compose-placeholder |
| **landscapist-placeholder** `com.github.skydoves` | 2.10.0 | Active | Apache-2.0 | **Yes — bundles Ktor** | partial | Shimmer for image loads | **Disqualified** | Violates no-network |
| **easy-shimmer-compose** | 0.0.2 | No | Apache-2.0 | **Yes — pulls Coil/OkHttp** | partial | Shimmer modifier + image | **Disqualified** | Violates no-network |
| **Facebook Shimmer / AndroidVeil** | 0.5.0 / 1.1.4 | Abandoned/stale | BSD-3/Apache | None | partial | View/XML shimmer | **Low** | View-based, not Compose |

#### Shared-element / screen transitions / nav-motion

| Library | Latest | Maintained? | License | Network? | Reduced-motion? | Purpose | Relevance | vs native/hand-roll |
|---|---|---|---|---|---|---|---|---|
| **Native `SharedTransitionLayout`** | in BOM (anim 1.7.4) | Native | Apache-2.0 | None | Yes | Matched shared-element / `sharedBounds` | **High** | **The native answer** — no dep; use for hero/glass-card |
| **Native `AnimatedContent`/`AnimatedVisibility`/`Crossfade`** | in BOM | Native | Apache-2.0 | None | Yes (`EnterTransition.None`) | Enter/exit, content swap | **High** | Already used app-wide |
| **material-motion-compose** `io.github.fornewid` | 2.0.1 (2024) | Stale | Apache-2.0 | None | partial | Container-transform / shared-axis | **Low** | Duplicates native `sharedBounds`/`AnimatedContent` |
| **accompanist-navigation-animation / -material** | 0.37.3 | **Deprecated** | Apache-2.0 | None | — | Nav transitions / sheet dest | **Disqualified** | Upstreamed into navigation-compose (app already does it) |

#### Shader / AGSL / RuntimeShader

| Library | Latest | Maintained? | License | Network? | Reduced-motion? | Purpose | Relevance | vs native/hand-roll |
|---|---|---|---|---|---|---|---|---|
| **Native `RuntimeShader` + AGSL** (API 33+) | platform | Native | platform | None | Yes (you gate) | Programmable shaders for glow ring + sheen | **High** | **No lib needed** — current hand-roll is correct |
| **skydoves Cloudy** (shader path) | 0.6.0 | Active | Apache-2.0 | None | Yes | Blur/liquid-glass accel + CPU fallback | **Med** | Optional accelerator for glass *surface* &lt;API 31; not ring/sheen |
| **composemeshgradient** `io.github.om252345` | 0.3.0 | Active | Apache-2.0 | None | Yes | Mesh gradients (GLES) | **Low** | Doesn't match LiquidGlass spec |
| **chigichan24/Spider · drinkthestars/shady** | alpha / sample | dormant / N/A | unclear/MIT | None | — | AGSL codegen / shader gallery | **Low (reference only)** | shady = copy-pattern reference, not a dep |

#### Tooltips / spotlight / coach-mark / reveal

| Library | Latest | Maintained? | License | Network? | Reduced-motion? | Purpose | Relevance | vs native/hand-roll |
|---|---|---|---|---|---|---|---|---|
| **Native Material3 `TooltipBox`/`PlainTooltip`/`RichTooltip`** | in BOM | Native | Apache-2.0 | None | Yes | Tooltips | **High (baseline)** | Native; prefer over 3rd-party |
| **reveal** `com.svenjacobs.reveal:reveal-core` | 4.3.0 (2026-06) | Active | MIT | None | Yes (programmatic) | Spotlight / coach-mark overlay | **High** | Native has NO spotlight — genuine gap-filler for onboarding |
| **Coachmark** `io.github.pseudoankit:coachmark` | 3.0.7 (2026-06) | Active | Apache-2.0 | None | Yes | Coach-mark sequence | **Med-High** | Alt to reveal |
| **Intro-showcase-view** `com.canopas` | 2.0.2 | Stale-ish | Apache-2.0 | None | partial | Feature-discovery showcase | **Med** | — |
| **Balloon** `com.github.skydoves:balloon-compose` | 1.7.6 | Active | Apache-2.0 | None | Yes | Styled arrow tooltips | **Med-Low** | Mostly duplicates M3 TooltipBox |

#### Physics / gesture / fling

| Library | Latest | Maintained? | License | Network? | Reduced-motion? | Purpose | Relevance | vs native/hand-roll |
|---|---|---|---|---|---|---|---|---|
| **Native gesture/physics** (`anchoredDraggable`, `SwipeToDismissBox`, `splineBasedDecay`, `spring()`) | in BOM | Native | Apache-2.0 | None | Yes (you own the specs) | Drag/swipe/fling/bouncy spring | **High** | Native — no lib needed |
| **swipe (SwipeableActionsBox)** `me.saket.swipe:swipe` | 1.3.0 | Active | Apache-2.0 | **None** (only compose ui+foundation) | partial (swap for static row) | Prebuilt swipe-to-reveal actions | **Med** | Replicable with `anchoredDraggable`; convenience only |

#### Particle / confetti — off-brand for a *quiet* app

| Library | Latest | Maintained? | License | Network? | Purpose | Relevance |
|---|---|---|---|---|---|---|
| **ParticleEmitter** `io.github.piotrprus:particle-emitter` | 1.1.0 (2026-06) | Active | Apache-2.0 | None | Ambient physics particles | **Med** (subtle drift only) |
| **Konfetti / ConfettiKit / Quarks / Persona** | 2.0.5 / 0.8.0 / stale / stale | mixed | ISC/MIT/Apache | None | Confetti/celebration | **Low** — clashes with quiet aesthetic |

> **Charts** were covered by both scouts; see §4a. Animated chart libs (Vico 3.2.2 stable, ComposeCharts 0.2.5, KoalaPlot 0.11.2) are all pure-Canvas/no-network and reduced-motion-gateable, but **YCharts is archived (disqualified)** and there is no charts surface in QuietPing today — do not add speculatively.

#### Haze — dedicated assessment (the one clear add)

Haze is the only library in either pass that is a genuine *upgrade* rather than a duplication. It does real **backdrop** blur — blurring the live content rendered *behind* a composable (a glass card floating over a scrolling list, a frosted top bar) — which native `Modifier.blur` provably cannot do (it blurs a composable's own pixels). That is precisely the limitation `Glass.kt`'s own KDoc admits ("True backdrop blur… a single Modifier cannot reach… approximated with the translucent fill + border"). Maintenance is excellent (Apache-2.0; repo pushed 2026-06-24; stable **1.7.2** Feb 2026; active 2.0 alpha line). Coordinates: **`dev.chrisbanes.haze:haze`** core, optional **`dev.chrisbanes.haze:haze-materials`** for ready-made `HazeMaterials`/`CupertinoMaterials` glass presets. Privacy-safe: pure on-device GPU, **no INTERNET, no networking dep**. Fits `minSdk 26` cleanly — real `RenderEffect` blur engages on **API 31+** and degrades to a **translucent scrim (no blur)** on API 26–30, so no crash and no false visual. Reduced-motion-friendly: blur is opt-in per call and toggleable, so it slots behind the existing `motionEnabled`/intensity gate. **Architectural note:** the 2.0 alpha splits blur into a separate `haze-blur` module configured via a `blurEffect {}` block — for production, adopt **stable 1.7.2 now**, treat 2.0 as a later migration.

---

## 5. Synthesis — verdict per candidate, mapped to LiquidGlass

The dominant finding: **QuietPing's design is already well-served by the hand-roll + native Compose.**
Glow border, sheen, motion gating, screen transitions, gestures, tooltips, charts-baseline — all either
hand-rolled correctly or available first-party in the current BOM. So most of the landscape is
**ALREADY-NATIVE** or **KEEP-HAND-ROLL**, and adding a lib there would violate "simplicity first."
Only a short list are genuine, invariant-safe upgrades.

| Candidate | Design token / gap it touches | Invariant outcome | Verdict |
|---|---|---|---|
| **Haze 1.7.2** | `Glass.kt` — its KDoc-admitted faked backdrop blur | Pure GPU, no net; API26–30 → scrim; gateable | **ADOPT** (the one clear design win) |
| **compose-placeholder 1.0.12** *or* **compose-shimmer 1.4.0** | Missing loading/skeleton states (UI_AUDIT) | No transitive deps; gateable | **ADOPT (optional)** — pick one |
| **Coil3 `coil-compose` only** | Media vault local-image render | **Net only if you add `coil-network-*`** — so don't | **ADOPT (optional)**, network module forbidden |
| **Telephoto `zoomable-android`** | Full-screen pan/zoom of vault media | Pure gesture, no net (base module only) | **ADOPT (optional)**, pair with local painter |
| **accompanist-permissions 0.37.3** | Runtime SMS/notification/media grants | No net, but EOL | **ADOPT (optional)**, plan to inline later |
| **Reorderable 3.1.0** | Reorder Rules / VIP / keyword priority | Pure Compose, no net | **CONSIDER** — only if reordering is a real requirement |
| **reveal 4.3.0 / Coachmark** | Onboarding spotlight (native has none) | UI-only, no net | **CONSIDER** — only if you want a guided tour |
| Bundled OFL font (resource) | `Type.kt` placeholder `FontFamily.Default` | A file in `res/font/`, no net | **CONSIDER** — the design-faithful fix; not a library |
| Glow border / sheen / liquid-glass shaders | `GlassEffects.kt` AGSL hand-roll | — | **KEEP-HAND-ROLL** — no lib beats it |
| Screen transitions, shared element, gestures, fling | `Motion.kt`, nav graph | — | **ALREADY-NATIVE** — `SharedTransitionLayout`/`AnimatedContent`/`anchoredDraggable` |
| Tooltips, date pickers, sheets, pager, flow layout, swipe-dismiss | various screens | — | **ALREADY-NATIVE** — in BOM 2024.10.01 |
| Lottie / Rive / Compottie | optional empty-state art | No net for bundled assets | **DEFER** — native `AnimatedVectorDrawable` covers quiet micro-interactions; reach for Lottie only for designed comps |
| Confetti (Konfetti/ConfettiKit), material-motion-compose, Cloudy, mesh-gradient | — | — | **SKIP** — off-brand / stale / duplicative |
| Accompanist (pager/flow/swiperefresh/systemui/nav-anim/placeholder), Orbital, YCharts, MPAndroidChart | — | — | **DISQUALIFIED** — deprecated/archived; first-party replacements exist |
| `landscapist-placeholder`, `easy-shimmer-compose`, Glide, Compose-ImageLoader | — | **Pull Ktor/OkHttp → break no-INTERNET** | **DISQUALIFIED** — networking dependency |

---

## 6. Recommendation, supply-chain risk & the "ask before adding" gate

### 6.1 Ranked shortlist (all invariant-safe)

**Tier 1 — the clear design win (1 dep):**
- **Haze 1.7.2** — gives the glass cards *real* backdrop blur, the gap `Glass.kt` documents.

**Tier 2 — close known UX gaps (each independent):**
- **A skeleton/shimmer lib** (`compose-placeholder` *or* `compose-shimmer`) — fixes "no loading state."
- **Coil3 `coil-compose` (no network module) + Telephoto `zoomable-android`** — render + zoom local vault media.
- **`accompanist-permissions`** — clean runtime-grant flow (only if the app isn't already doing this manually; plan to inline before Accompanist is fully removed).

**Tier 3 — only if the feature is actually wanted:**
- **Reorderable** (drag-reorder lists), **reveal/Coachmark** (onboarding spotlight), **bundled OFL font** (Type.kt fix), **Vico** (only if a stats/digest screen ships).

### 6.2 Exact `gradle/libs.versions.toml` additions (for whatever tier is approved)

```toml
[versions]
haze = "1.7.2"
composePlaceholder = "1.0.12"      # Tier 2 shimmer option A
# composeShimmer = "1.4.0"          # Tier 2 shimmer option B (pick one)
coil = "3.5.0"                      # Tier 2 media — CORE ONLY
telephoto = "0.18.0"                # Tier 2 media
accompanistPermissions = "0.37.3"   # Tier 2 permissions
reorderable = "3.1.0"               # Tier 3

[libraries]
haze = { group = "dev.chrisbanes.haze", name = "haze", version.ref = "haze" }
haze-materials = { group = "dev.chrisbanes.haze", name = "haze-materials", version.ref = "haze" }
compose-placeholder = { group = "com.eygraber", name = "compose-placeholder-material3", version.ref = "composePlaceholder" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }   # ⚠ NEVER add coil-network-okhttp / coil-network-ktor
telephoto-zoomable = { group = "me.saket.telephoto", name = "zoomable-android", version.ref = "telephoto" }   # ⚠ NOT zoomable-image-coil*
accompanist-permissions = { group = "com.google.accompanist", name = "accompanist-permissions", version.ref = "accompanistPermissions" }
reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }
```

### 6.3 Files each add would touch

| Add | Touches |
|---|---|
| Haze | `theme/Glass.kt` (provide a `HazeState`-backed real-blur variant of `Modifier.glass`), `components/GlassCard.kt`, and the scrollable backgrounds it floats over (Home/Vault scaffolds). Keep the API26–30 scrim path. |
| Shimmer | new `components/Skeleton*.kt`; wired into `home/HomeScreen.kt`, `rules/RulesScreen.kt`, `vault/VaultScreen.kt` loading states |
| Coil3 + Telephoto | a new vault media viewer (or `vault/VaultThreadScreen.kt`); reads `MediaVault`'s private `filesDir` copies |
| accompanist-permissions | `onboarding/OnboardingScreen.kt` / a permissions surface |
| Reorderable | `rules/RulesScreen.kt`, `rules/VipPicker.kt`, `rules/KeywordEditor.kt` |
| Bundled font | `theme/Type.kt` (`DisplayFontFamily`/`BodyFontFamily`) + `res/font/` |

### 6.4 Supply-chain risk by category

| Category | Risk | Note |
|---|---|---|
| Blur/glass (Haze) | **Low** | Pure render, Apache-2.0, no transitive network |
| Shimmer (compose-placeholder/compose-shimmer) | **Low** | No transitive deps. **High** for `landscapist-placeholder` (Ktor) / `easy-shimmer-compose` (OkHttp) → disqualified |
| Image loaders | **High by default** | Most pull a network stack. **Only Coil3 core without `coil-network-*`** is Low — requires discipline. Telephoto base = Low |
| Vector/Lottie/Rive | **Low** (bundled assets) | Rive bundles a native `.so` (APK size). Never use `.Url` Lottie spec |
| Transitions / gesture / shader / tooltips-baseline | **None** | Native — zero new supply chain |
| Reveal / Coachmark / Reorderable / Vico | **Low** | UI-only, Apache/MIT, no network |
| Permissions (accompanist-permissions) | **Low now, EOL risk** | Last surviving Accompanist module; plan an inline exit |

**Mandatory verification after ANY add:** run `./gradlew :app:dependencies` and confirm **no `okhttp`, `ktor`, or other HTTP client** appears, and that `AndroidManifest.xml` still has **no `INTERNET` permission**. That check is the invariant's enforcement, not the library's README.

### 6.5 Status — approved & implemented

Tier approved by the user: **Haze + shimmer + vault media**. Implemented (see §6.6). The version numbers
in §6.2 are the *researched latest*; the **actually-pinned** versions differ — see the critical note below.

### 6.6 Implementation note — toolchain-compatible version pins ⚠️

The "latest" mid-2026 versions in §6.2 **do not build on this project's toolchain.** QuietPing is on
**compose-bom 2024.10.01 (Compose 1.7.5) / AGP 8.7.3 / compileSdk 34**; the latest libs pull
JetBrains-Compose 1.8–1.10 (and `haze-materials` → `org.jetbrains.compose.material3:1.9.0`), which drags
`androidx.activity:1.12.2` and hard-fails `checkDebugAarMetadata` (demands AGP 8.9.1 / compileSdk 36).
Pinning each lib to its newest **Compose-1.7.x-compatible** release (verified against the published
`.module`/`.pom`) keeps the toolchain untouched:

```toml
# gradle/libs.versions.toml — actual working pins (NOT the §6.2 "latest")
haze = "1.2.2"            # 1.7.2 fails; haze-materials OMITTED (drags JB-Compose) — HazeStyle hand-built from tokens
composeShimmer = "1.3.2"  # 1.4.0 pulls JB-Compose 1.10
coil = "3.0.4"            # CORE only (coil-compose); NO coil-network-*
telephoto = "0.14.0"      # base zoomable-android (pure androidx 1.6.7); NOT the -image-coil bundle
```

What was built:
- **Haze** → real backdrop blur on the glass bottom bar (`ui/nav/QuietPingNavGraph.kt`): NavHost is the
  `hazeSource`, the bar is a `hazeEffect` clipped to its pill, `blurEnabled` gated on glass intensity,
  `HazeStyle` derived from `BgPrimary`/`GlassFill`. Auto-degrades to a scrim below API 31.
- **Shimmer** → `ui/components/LoadingShimmer.kt` `ShimmerBlock` now uses `compose-shimmer`
  `Modifier.shimmer()` internally; public API + reduced-motion gate unchanged (zero call-site churn).
- **Vault media** → Coil3-core thumbnails + Telephoto zoom gallery over `MediaVault`'s local files,
  through a proper `MediaRepository` (no UI→capture leak). *(Build-verified separately.)*

**Invariant guard:** after the adds, `:app:dependencies` must show **no `okhttp`/`ktor`** and the manifest
**no `INTERNET`** — that check is the enforcement, re-run on every future dep change.
