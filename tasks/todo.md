# Todo

## NEW: Fresh UX/UI audit + fix pass (2026-06-25) — `/goal` autonomous
Re-audited current source with 4 parallel agents (nav/state, a11y, visual/theming, motion/perf).
Stale UI_AUDIT.md was WRONG about fixes — all 3 CRITICALs still open. Reports in scratchpad/audit-*.md.
Collision rule: partition by file/package ownership, never by finding. Foundation serial (me) → screen agents parallel (1 per ui/<feature>/ pkg) → nav wiring (me) → build/test → re-audit loop.

### Confirmed-against-source (not just stale-audit claims)
- N1 decoy leak REAL: DecoyMode checked only in VaultViewModel:94; Home/VaultMedia/VaultThread VMs leak real content under decoy unlock. PRIVACY INVARIANT. (CRIT)
- C1/C2/C3 still open (Appearance/PrivacyLock unreachable; Vault row tap no-op). H1 rule-edit broken. (CRIT/HIGH)
- M3 zero `.catch` in any VM. N7 onboarding re-shows every launch (no completion flag). (HIGH/MED)
- MP-06 = FALSE POSITIVE (VaultThread already statusBarsPadding @ NavGraph:212). Dropped.
- H4 real count = 23 accent hardcodes (not 29). MP-01/02 = glow+sheen per Vault row (saveLayer/frame).

### WAVE 1 — shared foundation (me, serial, compile green before agents)
- [ ] theme/Color.kt: TextTertiary #94A3B8→#B4C0D0 (A11Y-08 contrast, app-wide)
- [ ] theme/Type.kt: tabular-nums style for counts (T2)
- [ ] theme/Spacing.kt NEW: spacing tokens (L6)
- [ ] theme/GlassEffects.kt: travelingGlowBorder stroke-not-saveLayer (MP-01)
- [ ] components: AccentSwitch NEW (M6) · ChoiceChip NEW selectable+Role.RadioButton+48dp (L4+A11Y-06) · AccentIconDisc NEW (L5) · quietPingFieldColors() (L6)
- [ ] components/EmptyState: gate icon reveal + liveRegion Polite (MP-04, A11Y-13)
- [ ] components/SegmentedControl: selectable Role.Tab + snap@reduced-motion + floor alpha (A11Y-10, MP-09/10)
- [ ] components/LoadingShimmer: hideFromAccessibility + Loading region (A11Y-12)
- [ ] components/PillBadge: tabular-nums + description param (T2, A11Y-14)
- [ ] components/AppToggleCard: toggleable Role.Switch + onCheckedChange=null + AccentSwitch + canonical glyph() (A11Y-01, M6, H5b)
- [ ] components/ListRow: ensure semantics (R2 adopt target)

### WAVE 2 — per-package screen agents (parallel, 1 pkg each, consume Wave1, NO gradle)
- [ ] home/ : H4(177,231) H5 dup-delete H5b-use-glyph A11Y-01 M6 M3+loading(N5) N1-decoy
- [ ] rules/ : H4(Rules225,Editor489) H1-add-onEditRule A11Y-03(KeywordEditor 18dp)/A11Y-06/A11Y-11 ChoiceChip adopt M3+loading L6-fields
- [ ] vault/ : C3-confirm-onOpenThread A11Y-04/05/14 N1-decoy(Media+Thread) M3 MP-02(drop row sheen) MP-14(coil placeholder)
- [ ] settings/ : H4(many) M4-mini-lib→shared A11Y-02(ToggleRow×11)/A11Y-07 ListRow adopt(R2) ChoiceChip/AccentIconDisc M3 + hub rows→Appearance/PrivacyLock/DeepCapture/About
- [ ] lock/ : H4(166) M5(AppLock 56dp btn→GlassButton) A11Y(error liveRegion Assertive)
- [ ] onboarding/ : H4(225,334) M5(fork buttons→GlassButton)
### WAVE 3 — nav wiring + new screens (me, after agents, against final signatures)
- [ ] Destinations: Dest.About; NavGraph: C3 onOpenThread, H1 onEditRule, About route, ensure Appearance/PrivacyLock reachable from hub
- [ ] AboutScreen NEW (version + permission status + privacy note) — hub target
- [ ] N7: onboarding-once (DataStore flag + startDestination + MainActivity)
### WAVE 4 — verify (me): DONE ✅ compileDebugKotlin green/warning-free; testDebugUnitTest 100 tests 0 fail; assembleDebug 42MB APK; merged manifest 0 INTERNET (src 0). No SettingsRepository fakes existed → interface widen cost 0.
### WAVE 5 — re-audit + fix loop: DONE ✅ CONVERGED CLEAN
  - Re-audit (4 fresh agents): motion CLEAN; found 4 must-fix the scoped pass missed:
    (a11y) RuleCard nested switch + RuleEditor SwitchRow×3 not migrated; (M3) AlertSettings+Appearance VMs missed `.catch`; (visual H5b) RulesScreen.icon()/VaultScreen.vaultIcon() still divergent from canonical glyph().
  - Fixed all 4 + 2 cleanups (dead `val accent` in About, dead `isFilteredEmpty`): RuleCard→role/onClickLabel+switch desc; SwitchRow→SettingToggleRow (deleted); both settings VMs +`.catch`/errorMessage + screen render; icon()/vaultIcon() deleted → canonical `glyph()` (ONE app-glyph map app-wide).
  - Confirmation re-audit (a11y+nav+visual on 9 changed files): VERDICT clean, 0 must-fix, 0 regression.
  - FINAL VERIFY: compileDebugKotlin green/warning-free; testDebugUnitTest 100 tests 0 fail; assembleDebug 42MB APK; merged manifest 0 INTERNET.
  - Deferred-LOW (documented, non-blocking, audit agreed): N6 VaultThread arg String-fallback (route is typed Long, works today); L6a some literal 16.dp gutters not yet swapped to `Spacing.ScreenH` (visually consistent); Type.kt placeholder fonts (intentional); MatchLog→Vault-list (no conversationId).
  - NOT committed (user hasn't asked). Changes in working tree. Pre-existing uncommitted PinHasher.kt left untouched (security, non-UI).
  - REMAINING (manual, resource-gated): live emulator smoke pass (boot + install + drive Settings hub / rule edit / vault thread / decoy mode) — not run here (disk 97% full; unit+build+audit cover the static surface).

## NEW: Competitor-feature bundles (2026-06-24) — `COMPETITOR_FEATURES.md`
Research done (4 categories). User approved ALL 4 bundles. On-device only (no INTERNET).
DB strategy: bump AppDatabase v1→v2 + `.fallbackToDestructiveMigration()` (ephemeral capture
data, retention-purged; avoids hand-written SQLCipher ALTER). New enums → Converters.
**Verify after EACH bundle** (lesson #12/#16): `export JAVA_HOME=/opt/homebrew/opt/openjdk@17`;
`./gradlew :app:compileDebugKotlin > log 2>&1; echo EXIT=$?`. Final: testDebugUnitTest + assembleDebug.

### Foundation (shared model) — do first, serial
- [ ] Models.kt: enums `AlertStyle{STANDARD,PERSISTENT,CRITICAL}`, `RuleAction{ALERT,SUPPRESS}`;
      extend `Rule` (+alertStyle, +action, +quietStartMin=-1, +quietEndMin=-1); pure `Rule.activeAt(minOfDay)`.
- [ ] Converters.kt: AlertStyle + RuleAction converters.
- [ ] RuleEntity.kt + Mappers.kt: new columns + mapping (defaults).
- [ ] PrivacySettings: +screenshotBlock,+hideNotificationContent,+decoyPinEnabled,+decoyPinHash,+breakInLogEnabled.
      New `AlertPrefs(digestEnabled,digestHour,otpAutoDeleteHours)` + repo flow/setter + DataStore keys.
- [ ] BreakInLogEntity + BreakInLogDao + repo; AppDatabase v2 (+entity,+dao,+fallbackToDestructiveMigration); DatabaseModule provider.

### Bundle 1 — Alert escalation
- [ ] NotificationChannels: critical channel (USAGE_ALARM sound + setBypassDnd).
- [ ] AlertDispatcher iface +cancelReminders(convId); Impl: PERSISTENT re-ping loop (scope job map, cancel on read),
      CRITICAL → setFullScreenIntent + alarm channel; RepeatSenderTracker (2× in N min → force CRITICAL) [pure+test].
- [ ] CapturePipeline.handleNotificationRemoved → alertDispatcher.cancelReminders(convId).
- [ ] Manifest: USE_FULL_SCREEN_INTENT.

### Bundle 2 — Time window + digest
- [ ] CapturePipeline.ingestAndMatch: filter matches by `rule.activeAt(currentMinOfDay)` (RuleEngine stays pure).
- [ ] DigestWorker (CoroutineWorker + EntryPoint) — recent matches → summary notif; schedule daily at digestHour; digest channel.
- [ ] RuleEditor UI: quiet-hours window pickers.

### Bundle 3 — Privacy quartet
- [ ] MainActivity: FLAG_SECURE reactive to privacy.screenshotBlock.
- [ ] AlertDispatcherImpl: inject SettingsRepository; hideNotificationContent → generic title/body + VISIBILITY_SECRET.
- [ ] Break-in log: AppLockViewModel logs on auth-error; PrivacyLockScreen shows count/list.
- [ ] Decoy PIN: hash+store+verify; PIN gate in AppLock; decoy → empty "decoy mode" vault (VaultViewModel honors flag).

### Bundle 4 — SMS productivity
- [ ] PatternCatalog: isOtp/extractOtp + isFinance/extractDue [pure+tests].
- [ ] RuleAction.SUPPRESS: CapturePipeline marks blocked instead of firing; RuleEditor action picker.
- [ ] OtpCleanupWorker: delete SMS msgs matching isOtp older than otpAutoDeleteHours.
- [ ] Local export: ExportManager (JSON) + ACTION_CREATE_DOCUMENT launcher in settings (no INTERNET).
- [ ] Finance: detection + immediate "bill detected" alert (scheduled reminder = future).

### Verify
- [x] compileDebugKotlin green after each bundle; final `testDebugUnitTest` (97 tests, 0 fail) + `assembleDebug` BUILD SUCCESSFUL (JDK17); merged manifest NO INTERNET; debugRuntimeClasspath NO okhttp/ktor/retrofit; USE_FULL_SCREEN_INTENT present.

### STATUS — ALL 4 BUNDLES DONE (2026-06-25)
- Foundation: Rule +alertStyle/+action/+windowStartMin/+windowEndMin; AlertStyle/RuleAction enums + Converters; PrivacySettings +5 fields; AlertPrefs new group; BreakInLog entity/dao/repo; DB v2 + `.fallbackToDestructiveMigration()` (concurrent session later bumped to v3 + added Conversation.watched — coexists cleanly).
- B1 Alert escalation: PERSISTENT re-ping loop (cancel on source-notif removal = read signal, keyed by conversationId via new read-only `conversationIdFor`), CRITICAL full-screen + ALARM-stream channel, `RepeatSenderTracker` (2× in 2min → force CRITICAL) [tested].
- B2 Time+digest: `Rule.activeAt` window gate applied in pipeline (RuleEngine stays pure) [tested]; `DigestWorker` (daily, IMPORTANCE_LOW channel) scheduled ~09:00; RuleEditor quiet-hours UI.
- B3 Privacy quartet: FLAG_SECURE (MainActivity reactive), content-hidden notifications (dispatcher), break-in log (AppLock onFailed → BreakInRepository, surfaced in Privacy screen), decoy PIN (`PinHasher` salted SHA-256 [tested] + `DecoyMode` singleton → empty vault). PrivacyLock UI + decoy dialog.
- B4 SMS productivity: PatternCatalog `isOtp`/`extractOtp`/`isFinance` [tested]; RuleAction.SUPPRESS keyword-block in pipeline + RuleEditor action picker; `OtpCleanupWorker` (purgeOtpOlderThan) scheduled 6h; AlertSettings digest + OTP-window UI.
- **DROPPED (deliberate):** local export — conflicts with the app's established "no export by design" stance (PrivacyLockScreen `ExportDisabledNote` + PRD §11). Noted in COMPETITOR_FEATURES.md.
- **Deferred (honest):** finance SMS detection shipped+tested but not surfaced (no FINANCE trigger/UI); digest hour-alignment best-effort; decoy-mode hides the Vault list (thread unreachable since no tiles).
- Live emulator pass for the new flows = follow-up (user).


## NEW: Component audit + component-local refactors (2026-06-24) — `COMPONENT_AUDIT.md`
Audited all 9 `ui/components/`. Thesis: NOT a custom→Material3 swap job (that'd kill the LiquidGlass look); wins = consistency, dedup, dead code. Verdicts in COMPONENT_AUDIT.md.
User approved: implement all component-local fixes (R1+R3+R4+R5), defer R2 (screen dedup — concurrent-session collision risk).
- [x] R1 — `GlassButton` → shared `pressElevation` (+ `Motion.PressScaleButton = 0.96f`; removed no-op Ghost transparent border + dead press imports). Press language now uniform Card/Row/Button.
- [x] R3 — `AppToggleCard` `Icons.Filled.Message` → AutoMirrored. Build now WARNING-FREE.
- [x] R4 — `PillBadge` count rolls via `AnimatedContent` (slide+fade); reduced-motion → static instant.
- [x] R5 — `SegmentedControl` indicator → real `glass()` frosted pill + accent wash (was flat tint).
- [x] VERIFIED MYSELF: compileDebugKotlin + assembleDebug + testDebugUnitTest all BUILD SUCCESSFUL (JDK17); 0 warnings; no new deps; no INTERNET.
- [ ] R2 (DEFERRED) — `ListRow` is DEAD (0 uses) while AlertSettings(7)/PrivacyLock(10)/RuleEditor(9)/Appearance(6) hand-roll rows inline. Adopt `ListRow`+`SectionHeader` across them (or delete ListRow). Needs settled tree (touches screens other sessions edit).

## NEW: Premium motion pass (2026-06-24) — workflow `premium-motion-pass`
Goal: make the app feel premium + smooth (more effects beyond existing motion).
- v1 workflow degraded: 2/3 parallel design agents died to multi-session API contention. STOPPED + relaunched v2 (embedded the surviving agent's full plan, concurrency capped at 2). See lessons.md.
- [x] Foundation — `ui/theme/Motion.kt`: `cascadeIn(index)` + `LazyItemScope.cascadeItem(index)` (staggered first-paint list cascade, clamped to MaxCascade=6), `riseIn(stepIndex)` (sequential section assemble, modalSpring), `pressElevation(interactionSource, shape, …)` (scale + accent-tinted lift shadow). All reduced-motion-gated (`!motionEnabled → return this`); deferred graphicsLayer reads; named tokens; no magic numbers.
- [x] Apply — components: GlassCard + ListRow swapped hand-rolled flat press-scale → `pressElevation` (deleted dead anim/imports). screens: Home/Rules/AlertSettings list items `animatedItem()` → `cascadeItem(index)` (bare, LazyItemScope receiver); headers/sections → `riseIn(0/1/2)`.
- [x] cascadeItem receiver bug (called as `Modifier.cascadeItem` → mismatch) corrected to bare call before run end. lessons.md updated.
- [x] VERIFIED MYSELF (not just workflow report): `compileDebugKotlin` + `assembleDebug` + `testDebugUnitTest` all BUILD SUCCESSFUL (JDK17); debugRuntimeClasspath has NO okhttp/ktor; manifest NO INTERNET; reduced-motion gate confirmed by reading Motion.kt.
- [ ] Follow-up (needs settled tree + API26 device): live FPS profiling + visual check; optional nav bottom-bar indicator polish (deferred — hot multiply-edited file).

## NEW: Face 2 — Xposed/LSPosed module (in-chat deleted-message recovery) (2026-06-24)
RD: `FACE2_XPOSED_RD.md`. Shows revoked msgs inside target app's OWN chat UI, root+LSPosed only.
Per-app gated from QuietPing via XSharedPreferences. Xposed API = compileOnly (NOT in APK; no-INTERNET safe).
- [x] Build wiring: libs.versions.toml xposed api 82; settings.gradle.kts api.xposed.info repo; build.gradle compileOnly
- [x] Manifest: xposedmodule/description/minversion/scope meta-data; strings xposed_description; arrays xposed_scope (5 pkgs: WA+w4b, IG, Messenger+FB)
- [x] assets/xposed_init -> com.quietping.xposed.QuietPingXposedModule
- [x] xposed/XposedGate.kt (prefs file + per-pkg key + XSharedPreferences read, fail-safe false)
- [x] xposed/QuietPingXposedModule.kt (IXposedHookLoadPackage; gate-check; dispatch by package; catch-all fail-safe)
- [x] xposed/WhatsAppRevokeHook.kt (block-revoke hook point STUB; obfuscated method lookup = documented TODO)
- [x] Verify: compileDebugKotlin + assembleDebug BUILD SUCCESSFUL (JDK17); merged manifest 0 INTERNET; APK 0 de.robv.* classes; xposed_init shipped
- [x] FOLLOW-UP suite (workflow `quietping-face2-suite`, 4 parallel agents + verify):
  - [x] Face 1: `root/RootManager` (su-probe root + /data/adb/lspd LSPosed detect, fail-closed, @Inject @Singleton)
  - [x] Face 1: `data/repo/RootGateRepository` (writes MODE_WORLD_READABLE gate via XposedGate keys; degrades on non-LSPosed) + `ui/settings/DeepCapture{Screen,ViewModel}` (per-app toggles or unavailable explainer) + nav (`Dest.DeepCapture`, statusBarsPadding) + PrivacyLock entry row
  - [x] Face 2: `xposed/InstagramUnsendHook` + `xposed/FacebookRemoveHook` (mirror WA hook, fail-safe inert) + dispatch wired in QuietPingXposedModule
  - [x] Face 2: `xposed/WhatsAppRevokeMatcher` (reusable behavioural-signature matcher) + WhatsAppRevokeHook.findRevokeHandler wired (no-match sentinel until RE) + `WHATSAPP_RE_PROCEDURE.md`
  - [x] SELF-VERIFIED (not just workflow report): compileDebugKotlin+testDebugUnitTest+assembleDebug BUILD SUCCESSFUL, 70 tests; merged manifest 0 INTERNET; dexdump = 0 DEFINED de.robv classes (only 4 type refs, correct for compileOnly).
  - [x] SEQUENTIAL FIXES (each compile/test-verified myself, JDK17):
    - [x] Issue 1 (WhatsApp recovery): added DURABLE `SqliteWriteHook` (hooks framework `android.database.sqlite.SQLiteDatabase` — never obfuscated, always attaches) + `WhatsAppDbRevokeStrategy` (UPDATE block-revoke). OBSERVE-ONLY by default (`REVOKE_SCHEMA_VALIDATED=false`) — logs candidate revoke writes, mutates nothing until schema confirmed on-device (no reckless DB corruption). Obfuscated matcher kept as precise-secondary.
    - [x] Issue 2 (IG/FB): both delegate to shared `SqliteWriteHook` (DELETE = unsend/remove), observe-only, documented table anchors. No boilerplate triplication.
    - [x] Issue 3 (vault bridge): BUILT, not design-only. `RawEvent.DeepHookRecovered` + `CaptureSource.DEEP_HOOK` → `CapturePipeline.handleDeepHookRecovered` (resolve+ingest+match). Face2 `VaultBridge` sends explicit-component Intent + shared token (`XposedGate` bridge consts); Face1 `DeepCaptureReceiver` (exported, token-gated, @AndroidEntryPoint) → `pipeline.offer`. `RootGateRepository.bridgeToken()` gens/persists token. Manifest receiver added. Plaintext transits Binder only → SQLCipher; encrypted-at-rest preserved.
    - [x] VERIFIED: compileDebugKotlin+testDebugUnitTest+assembleDebug BUILD SUCCESSFUL; 8 test classes pass; merged manifest 0 INTERNET; dexdump 0 DEFINED de.robv classes; new bridge/hook classes present in dex.
    - [x] SECURITY FIX (review HIGH: spoofable token in world-readable prefs): replaced token+exported-BroadcastReceiver bridge with caller-UID-authenticated `DeepCaptureProvider` (ContentProvider.call → Binder.getCallingUid must resolve to a TARGET_PACKAGES app; PackageManager-checked). Deleted `RootGateRepository.bridgeToken()`, token consts, `DeepCaptureReceiver`. Hilt via EntryPointAccessors (provider can't be injected). Manifest receiver→provider. Verified: compile+test+assemble green, 0 INTERNET. (signature-perm rejected: legit sender is the target app's UID, not our signature.)
    - [x] TEST COVERAGE for the bridge Face-1 path: 2 new `CapturePipelineIntegrationTest` cases prove `RawEvent.DeepHookRecovered` resolves a conversation, archives with `CaptureSource.DEEP_HOOK`, and still fires rules on keyword match. Full suite 72 tests, 0 failures (JDK17).
    - [x] Repackaged-APK ("modified version of connected app") request: wrote DESIGN-ONLY `WHATSAPP_REPACKAGE_PLAN.md` (apktool/smali patch points, re-sign, root pm install, full risk list). DECLINED to build the auto pull→patch→re-sign→install pipeline (copyright-circumvention + forged-sig installer; data loss; bans). Recommended Xposed module instead — same outcome, reversible, no re-sign.
    - [ ] Issue 4 (live device test): CANNOT run here — needs rooted+LSPosed hardware + real apps. Delivered `FACE2_DEVICE_TEST.md` checklist (also the RE step to flip observe→block). USER must execute.
    - [x] PRE-READ BODY RECOVERY (closes the "no body" gap WITHOUT blocking): `SqliteWriteHook` now reads the targeted row READ-ONLY (`db.query` on `param.thisObject`, same where/whereArgs) the instant before the revoke/unsend write erases it, then `VaultBridge.send` → vault. Heuristic schema-agnostic field extraction (BODY/SENDER/THREAD/TIME anchors + longest-text fallback) + logs column names for RE. All 3 apps pass `recover=true`; works the moment the hook attaches, independent of the riskier in-chat block. Verified: compile+72 tests+assemble green, 0 INTERNET, 0 defined de.robv. (Transient build break = concurrent session's SettingsRepositoryImpl, NOT my files — self-resolved.)
    - [ ] Still needs user/device: confirm WhatsApp revoke column + narrow IG/FB DELETE detector BEFORE flipping `validated=true` (in-chat block); tighten recovery anchors from RECOVER logcat if heuristic mis-picks; obfuscated-handler RE optional (SQLite pre-read now covers vault recovery without it).


## NEW: Alert notification deep-link to thread (2026-06-24)
Tapping an alert opens the exact VaultThread conversation it fired from.
Approach: Intent-extra (NO manifest intent-filter / navDeepLink → no Play-review risk, on-device).
- [x] AlertDispatcherImpl.launchIntent(conversationId, requestCode): EXTRA_CONVERSATION_ID extra; notificationId as PendingIntent requestCode (extras don't collide across alerts).
- [x] AndroidManifest: MainActivity launchMode="singleTop" (onNewIntent delivers taps to a running app).
- [x] MainActivity: parse extra in onCreate + onNewIntent → deepLinkThreadId mutableStateOf<Long?>; pass to QuietPingNavGraph(deepLinkThreadId, onDeepLinkConsumed).
- [x] QuietPingNavGraph: optional params + LaunchedEffect → NavHostController.openThreadFromAlert(): seats Vault root then pushes Thread (Back → Vault list).
- [x] Verify: compileDebugKotlin + testDebugUnitTest BUILD SUCCESSFUL (JDK17).
- [ ] Live emulator tap-through (user).

## NEW: Captured-media gallery (2026-06-24)
Surface MediaVault files in a browsable gallery (grid -> full-screen pan/zoom), reachable from Vault.
Deps already added (Coil3 3.0.4 core, Telephoto 0.14.0 zoomable-android). NO toml/gradle edits. NO INTERNET.
javap-verified APIs (cache 3.0.4 / 0.14.0):
- Coil3 `coil3.compose.AsyncImage(model, contentDescription, modifier, contentScale, ...)` (singleton wrapper, no ImageLoader arg) + `rememberAsyncImagePainter(model)`. core has FileUriFetcher/ContentUriFetcher/BitmapFactoryDecoder => local File/Uri loads with zero network.
- Telephoto base: `rememberZoomableState(ZoomSpec(maxZoomFactor))` + `Modifier.zoomable(state)` + `state.setContentLocation(ZoomableContentLocation.Companion.scaledInsideAndCenterAligned(size))` (base module, NOT image-coil bundle).
- [x] domain MediaItem (Models.kt) + MediaRepository (Repositories.kt) + MediaRepositoryImpl (data, @ApplicationContext, parses name, refresh-triggered Flow on IO) + Hilt @Binds in RepositoryModule
- [x] VaultMediaUiState + VaultMediaViewModel (@HiltViewModel, StateFlow, refresh on init) + VaultMediaScreen (LazyVerticalGrid Coil thumbs, ShimmerBlock loading, EmptyState empty, full-screen Telephoto zoom overlay w/ BackHandler + setContentLocation on decode)
- [x] Dest.VaultMedia (Destinations.kt) + QuietPingNavGraph route (screen owns status-bar inset so viewer is full-bleed) + VaultScreen SectionHeader trailing PhotoLibrary IconButton -> onNavigate(Dest.VaultMedia)
- [x] Verify: compileDebugKotlin + assembleDebug BUILD SUCCESSFUL (JDK17); debugRuntimeClasspath grep okhttp|ktor = EMPTY; source+merged manifest = NO INTERNET. (A transient compile break in a CONCURRENT agent's AlertDispatcherImpl.kt deep-link work appeared mid-build then self-resolved — not my code.)

## NEW: Kotlin/Compose component + effect library catalog (2026-06-24)
Request (/deep-research): list all component + effect libs for Kotlin, apply based on design.
Blockers found: no DESIGN.md (design source of truth = `ui/theme/`); GEMINI_API_KEY unset → used my knowledge + WebSearch + 2 parallel research subagents instead.
- [x] `LIBRARY_CATALOG.md` written: invariant filter, LiquidGlass inventory, design gaps, §4a component tables, §4b effect tables + Haze deep-dive, §5 verdict map, §6 ranked shortlist + exact `libs.versions.toml` lines + per-file touches + supply-chain-by-category + ask-gate.
- [x] Verdict: design already served by hand-roll/native (glow/sheen/motion/transitions/gestures). One clear ADD = **Haze 1.7.2** (real backdrop blur — fixes `Glass.kt`'s self-admitted faked blur). Tier-2 optional: shimmer (compose-placeholder/compose-shimmer), Coil3-core+Telephoto (vault media), accompanist-permissions. DISQUALIFIED on no-INTERNET: landscapist-placeholder (Ktor), easy-shimmer-compose (Coil/OkHttp), Glide.
- [x] User approved tier: **Haze + shimmer + vault media**. Deps added to catalog + `app/build.gradle.kts`.
- [x] Version-skew hit + fixed: latest libs pulled androidx.activity 1.12.2 → compileSdk 36. PINNED to Compose-1.7.x line (verified via published .module/.pom): Haze **1.2.2** (dropped `haze-materials`), compose-shimmer **1.3.2**, Coil3 **3.0.4**, Telephoto **0.14.0**. See lessons.md.
- [x] **Haze** wired into `QuietPingNavGraph.GlassBottomBar` — real backdrop blur of scrolling content (NavHost = `hazeSource`, bar = `hazeEffect` + clip-to-pill), `blurEnabled` gated on glass intensity, HazeStyle from LiquidGlass tokens (`BgPrimary`/`GlassFill`). API javap-verified. `compileDebugKotlin` BUILD SUCCESSFUL.
- [x] **Shimmer** — `ui/components/LoadingShimmer.kt` `ShimmerBlock` internals swapped to `compose-shimmer` `Modifier.shimmer()`; public API + motion gate unchanged (all call sites untouched). BUILD SUCCESSFUL.
- [~] **Vault media gallery** (Coil3 core + Telephoto) — delegated to subagent: MediaRepository (domain+data wrapping `MediaVault.list`) + Hilt binding + VaultMediaViewModel + VaultMediaScreen (grid thumbnails + full-screen zoom) + Dest route + VaultScreen entry point. Awaiting build verification.
- [ ] Final: re-verify `assembleDebug` + `testDebugUnitTest` myself; `:app:dependencies` confirms NO okhttp/ktor; manifest has NO INTERNET. Update LIBRARY_CATALOG §6 with the pinned versions.

## NEW: Vault UI polish — sheen + travelling glow border (2026-06-24)
Request: more effects than plain green bubbles; add sheen; travelling glowing border on largest tile class.
Decision: largest tile class = GlassCard. Effects reusable + motion-gated.
- [x] `ui/theme/GlassEffects.kt` — `Modifier.travelingGlowBorder()` (rotating sweep-gradient ring via offscreen saveLayer + drawRoundRect/BlendMode.Clear punch) + `Modifier.sheen()` (slow diagonal highlight sweep). Honor motionEnabled; reduced-motion → static accent edge / no sheen.
- [x] `ui/components/GlassCard.kt` — added `glow`/`sheen`/`glowColors` opt-in params, wired after `.glass()`.
- [x] Apply: VaultScreen ConversationRow (accent glow + sheen); VaultThreadScreen EDITED (StatusWarning glow) + DELETED (StatusAlert glow) + sheen; ACTIVE left plain (perf).
- [x] Verify: `:app:compileDebugKotlin` BUILD SUCCESSFUL + `:app:testDebugUnitTest` BUILD SUCCESSFUL.
- [x] Live emulator verify: temp-seeded vault (reverted after), screenshotted Pixel_10_Pro_XL — travelling emerald→teal glow border + diagonal sheen render on the GlassCard conversation tiles; lobe orbits (captured top/right/top-right across frames). Temp seed in AppInitializer removed; rebuilt + reinstalled clean + wiped device data.

## Enable direct download from GitHub (2026-06-24)
- [x] CI workflow `.github/workflows/release.yml` — builds debug APK, attaches to Release on `v*` tag
- [x] README.md with Download section + release badge
- [x] INSTALL.md "Part 0 — Download a prebuilt APK"
- [x] Push to GitHub + cut first release tag (`v1.0`) — release live
- [x] Verify Actions run green + APK on Releases page (QuietPing-v1.0-debug.apk, 41MB)

## UI motion polish (2026-06-24)
- [x] Shared `ui/theme/Motion.kt` — reduced-motion-aware helpers: `animatedItem()` (LazyItemScope), `Modifier.animateSizeChange()`, `motionEnter()/motionExit()`, `motionScaleIn()`. Gate = LocalQuietPingTheme.motionEnabled; off → short fade, no translation/scale.
- [x] Nav: screen push/pop slide + tab crossfade (prior task).
- [x] Lists: `animatedItem()` on every LazyColumn items{} (Home apps+feed, Vault, VaultThread msgs, Rules, AlertSettings).
- [x] Reveals: empty-states / conditional sections via AnimatedVisibility(motionEnter/Exit); icon discs via motionScaleIn (EmptyState, AppLock shield, Appearance check badges).
- [x] Size: animateSizeChange on KeywordEditor chips, RuleEditor matcher swap, VipPicker list, PurgeRow, AlertCard.
- [x] Selection fades: PresetChip/RetentionChip/AccentSwatch/IconChoice via animateColorAsState+signatureSpring.
- [x] Press feedback: ListRow press-scale 0.98 (GlassCard pattern).
- [x] Verify: `:app:assembleDebug` BUILD SUCCESSFUL (JDK17). NOTE: capture/domain layer under concurrent bg edit — UI changes compiled clean (zero UI errors); transient backend signature mismatch self-resolved.

## BUGFIX: full-screen back button untappable (2026-06-24)
- Symptom: New-rule editor on-screen back arrow did nothing; system back worked.
- Root cause: full-screen nav routes render edge-to-edge with NO status-bar inset → inline back-header under the status bar window, which eats the taps. (Bottom-nav roots OK via Scaffold contentPadding.)
- Fix: wrap RuleEditor/VaultThread/Appearance/PrivacyLock in `Box(Modifier.statusBarsPadding())` in QuietPingNavGraph.
- [x] Verified live on Pixel_10_Pro_XL: Back node moved [84,60]→[84,219] (below status bar); tapping arrow returns to Rules. assembleDebug green.

## Current State
- PRD complete: `PRD.md`.
- Implementation workflow RUNNING (bg): QuietPing Android app. Task wx2qh38o2 / wf_025db45e-103.

## In progress (workflow)
- [x] Foundation (build system + manifest + resources + app entry points): DONE. `gradle :app:help --offline` BUILD SUCCESSFUL; wrapper pinned 8.10.2; 7 silent WAV presets generated.
- [ ] Implementation: data, capture, domain, ui-core, screens x4, icon
- [ ] Integration: Hilt DI, nav wiring
- [ ] Verification: unit tests + gradle build (BUILD_STATUS.md)

## SCREENS SET 2 — Message Vault (subagent, com.quietping.ui.vault)
- [x] Read PRD + DESIGN + all foundation contracts + sibling screens + all 8 components
- [x] VaultUiState.kt — filter (All/Deleted/Edited) + search + ConversationSummary list
- [x] VaultViewModel.kt — MessageRepository: combine conversations()+deleted()+edited() -> counts + last msg; filter+search
- [x] VaultScreen.kt — SegmentedControl filter + glass search field + GlassCard rows -> onOpenThread(id)
- [x] VaultThreadViewModel.kt — MessageRepository.thread(id) via SavedStateHandle
- [x] VaultThreadScreen.kt — ACTIVE normal; EDITED PillBadge + expandable version diff; DELETED "Recovered" strikethrough; reduced-motion
- [x] Verified all Compose/icon/coroutine APIs against real gradle-cache jars (icons 1.7.8, m3 1.3.1, coroutines 1.9.0)
- Key decision: screen keeps mandated (onNavigate,onBack,viewModel) + adds onOpenThread:(Long)->Unit default; NavGraph wires createRoute.

## Emulator run — DONE (2026-06-24)
- [x] assembleDebug (41MB APK) + install on Pixel_10_Pro_XL (android-37)
- [x] Launch clean (no crash); drove Onboarding/Home/Rules/Vault/Alert-settings; bottom nav OK
- [x] Theme fidelity verified live; activity-alias launcher works

## Audits — DONE
- [x] UI/UX audit (w8fun1mnb) -> UI_AUDIT.md. 64/100; 3 critical (Appearance/Privacy unreachable; Vault tap no-op), 5 high. (a11y dimension killed by cyber-safeguard false-positive.)
- [x] Backend adversarial audit (w6gejr3oi) -> stopped after decisive finding extracted.

## CRITICAL BUG (backend audit) — FIX RUNNING (wynxc8jlz)
- Parsers emit conversationId=0; NO code creates Conversation row; resolveConversationId has zero callers.
  -> FK insert fails (swallowed) => Vault empty; appPackageFor()=null => RuleEngine/AlertDispatcher never run for notifications; markDeleted dead; MatchLog.messageId=0.
  -> 59 tests passed only because they pre-seed conversationId=10 (bypass integration). No CapturePipeline integration test existed.
- [x] Fix: conversation resolved+stamped before ingest; appPackageFor removed; VaultManager.ingest returns id; SMS resolve; CapturePipelineIntegrationTest added (starts conversationId=0). 69 tests pass (incl 3 new).
- [x] Rebuild APK + reinstall on emulator — launches clean, no crash (2026-06-24).

## Other findings to address later
- MMS advertised but never read (only SMS) — SmsObserver
- No runtime DND-policy grant request
- UI: rules can't be edited (always NEW); Home/Rules no loading state; ~29 hardcoded Emerald400; no error states; Glass.kt no real blur; fonts = Default placeholders

## After workflow
- Review BUILD_STATUS.md; fix any residual compile errors
- Resolve PRD §14 open questions (SMS strategy, retention default, icon variants)
- Replace placeholder silent WAVs with designed alert tones

## NEW: capture-technique hardening (2026-06-24, from deleted-message research)
Gap analysis vs research — already had: notif listener, a11y, SMS diff, edit/version, revoke sentinel.
Adding the 4 genuinely-missing techniques:
- [x] 1. Active-notification snapshot — `onListenerConnected` ingests `activeNotifications`; filtered to supported packages; VaultManager dedupes re-ingests.
- [x] 2. Removal reason codes — 3-arg `onNotificationRemoved`; `RawEvent.NotificationRemoved.reason`; pipeline drops `USER_DISMISS_REASONS` (click/cancel/cancel-all/listener-cancel).
- [x] 3. Deletion-diff hardening — `DeletionDiffer` + `SeenMessage`; per-message MessagingStyle `time` threaded via `messageTimes`; LRU snapshot map in pipeline; 7 unit tests.
- [x] 4. Media capture — `MediaObserver` (3 MediaStore collections, path-marker filter, baseline-primed) + `MediaVault` (private filesDir copy, dedupe by id). READ_MEDIA_* / READ_EXTERNAL_STORAGE(maxSdk 32) in manifest.
- [x] Verify: `compileDebugKotlin`+KSP/Hilt BUILD SUCCESSFUL; full unit suite 66 tests, 0 fail (incl. 7 new). UI surfacing of captured media = follow-up.

## NEW: screen transition animations (2026-06-24)
- [x] NavHost enter/exit/popEnter/popExit in QuietPingNavGraph.kt.
- [x] Forward = horizontal push (slideIntoContainer Start + signatureSpring fade); back = reverse (End).
- [x] Tab swaps between bottomNavRoots = crossfade (no directional slide).
- [x] Reduced-motion gate (LocalQuietPingTheme.motionEnabled) → fade-only tween(150), no translation.
- [x] Existing MotionTokens (offsetSpring/signatureSpring); no new deps, no INTERNET. compileDebugKotlin BUILD SUCCESSFUL.

## NEW: watch-specific-group-chats (2026-06-25)
Decisions: scope = both SMS + app groups · logic = mute-gate BEFORE RuleEngine (group-only) · UI = per-row watch toggle on existing Vault list · default = watched.
- [x] Conversation.watched (domain Models + ConversationEntity col + Mappers); DB v2→v3 (destructive-migration, no ALTER)
- [x] ConversationDao.setWatched; MessageRepository.conversationById + setWatched; impl
- [x] Gate in CapturePipeline.ingestAndMatch: archive always; if conv.isGroup && !watched → skip rule eval
- [x] SMS group detection in SmsObserver (recipient_ids count); ingestSms carries isGroup
- [x] Vault row watch toggle (only on group rows) → VaultViewModel.setWatched
- [x] Tests: SMS group recipient parse (pure) + muted-group gate (pipeline) + setWatched round-trip; fakes updated
- [x] assembleDebug + testDebugUnitTest green
  DONE 2026-06-25: compileDebugKotlin + testDebugUnitTest (8 pipeline incl 3 new, 5 SMS detection, 0 fail) + assembleDebug all green. JDK17=/opt/homebrew/opt/openjdk@17.
