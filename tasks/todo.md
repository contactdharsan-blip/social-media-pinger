# Todo

## Enable direct download from GitHub (2026-06-24)
- [x] CI workflow `.github/workflows/release.yml` — builds debug APK, attaches to Release on `v*` tag
- [x] README.md with Download section + release badge
- [x] INSTALL.md "Part 0 — Download a prebuilt APK"
- [ ] Push to GitHub + cut first release tag (`v1.0`) to produce the downloadable APK
- [ ] Verify Actions run green + APK on Releases page

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

## Audits — RUNNING
- [ ] Backend adversarial audit (w6gejr3oi) -> BACKEND_AUDIT.md (is it lying? + per-app coverage)
- [ ] UI/UX audit (w8fun1mnb) -> UI_AUDIT.md

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
