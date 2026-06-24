# Todo

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

## After workflow
- Review BUILD_STATUS.md; fix any residual compile errors
- Resolve PRD §14 open questions (SMS strategy, retention default, icon variants)
- Replace placeholder silent WAVs with designed alert tones
