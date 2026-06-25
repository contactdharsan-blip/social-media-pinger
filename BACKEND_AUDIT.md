# QuietPing — Honest Backend Audit

**Scope of the two questions**
1. **Is the app lying?** — Are there claims (PRD / KDoc / UI) that the code does not actually deliver?
2. **Does it actually work for all targets?** — WhatsApp, Instagram, Messenger, Facebook, SMS.

**Method.** Every finding below was read directly from source and cross-checked with codebase-wide greps. Each negative was independently re-derived (not taken on faith from the input), and the PRD was read to separate *honestly-disclosed limitations* from *actual dishonesty*. Unit tests were read and traced (they could not be executed: AGP 8.7.3 needs JDK 17; isolation correctness was verified by reading the tests + tracing code).

---

## TL;DR Verdict

**The app is partially lying — and it does NOT work end-to-end for ANY target.**

- The individual building blocks (parsers, Aho-Corasick, RuleEngine, PatternCatalog, AlertDispatcher, notification channels, SMS reader, purge worker, theming, onboarding) are **genuinely and competently implemented**. This is not a stub farm.
- But a **single, fatal, load-bearing integration is missing**: no capture path ever creates a `Conversation` row. Because of that one gap, **both** user-facing capabilities — the conditional ping **and** the message Vault — are **completely dead on a real device for every app**, while every class in isolation reads as finished and most unit tests pass.
- The dishonesty is **not** in fake functions. It is in the **PRD claiming an end-to-end data flow and "every message written the moment it arrives" that the code provably cannot execute** (FK violation on every insert + unreachable RuleEngine).
- The often-suspected "lie" — *that chat apps secretly need more than notifications* — is **NOT a lie**: the notification-only limitation is explicitly and honestly documented in the PRD.

---

## CLAIMS vs REALITY

| Claim | PRD / code says | What the code actually does | Verdict | Evidence |
|---|---|---|---|---|
| **End-to-end capture flow** | "source → parser → Message → RuleEngine (match?) → AlertDispatcher (ping) **and** VaultManager (archive) → Room" | Parser→Message works; then `vaultManager.ingest` hits an FK violation (nothing archived) **and** `appPackageFor()` returns null → `return` **before** RuleEngine/AlertDispatcher. Neither half of the flow completes. | **FALSE** | PRD.md:109-111; CapturePipeline.kt:238-262 |
| **"Every incoming chat message is written to the encrypted store the moment it arrives"** | PRD §6D capture promise | Every message insert is attempted with `conversation_id = 0`, which violates the FK → `SQLiteConstraintException`, swallowed by `runCatching`. Vault stores **nothing**. | **FALSE** | PRD.md:196-197; MessageRepositoryImpl.kt:74-77; MessageEntity.kt:22-27; DatabaseFactory.kt:55; CapturePipeline.kt:84-85 |
| **Conditional re-notify across the 4 chat apps** | PRD §1/§5: "alerts only when it matters", per-app trigger matrix | RuleEngine is never reached for notification-sourced messages (early return on null app). No alert can fire for WhatsApp/Instagram/Messenger/Facebook. | **FALSE** | PRD.md:117-124; CapturePipeline.kt:243,247-249,259-261 |
| **Deleted → Recovered (Vault)** | PRD §6D | `VaultManager.markDeleted` looks up the conversation row; `conversations()` is always empty → always returns early. All 3 deletion routes are dead. | **FALSE** | PRD.md:199-200; VaultManagerImpl.kt:128-130; CapturePipeline.kt:147,200,234 |
| **Match feed / audit log** | PRD §9.1 | `MatchLog.messageId = match.message.id` is always 0 (persisted id never written back); FK to `messages.id` would also throw even if a match fired. | **FALSE** | AlertDispatcherImpl.kt:77; MatchLogEntity.kt:17-22 |
| **All 6 TriggerTypes really implemented (no silent fall-through)** | "exhaustive when, no else" | True **in isolation**: exhaustive Kotlin `when` over `TriggerType`, one real branch each, no `else`. KEYWORD=Aho-Corasick, NAME/REPLY=automaton/@handle/sentinel, POLL/GROUP=per-app regex, VIP=whole-word sender. | **TRUE** | RuleEngineImpl.kt:55-101; PatternCatalog.kt:37-187 |
| **Capture is event-driven, no polling** | efficiency claim | No `while(true)`/`Thread.sleep`/`AlarmManager`/`JobScheduler`/timers in capture paths; OS callbacks + ContentObserver only. (SMS/Media re-query the provider on each `onChange` — event-triggered, not interval polling.) | **TRUE** | PingNotificationListenerService.kt:77; SmsObserver.kt:60-72; PingAccessibilityService (config) |
| **Listener/Accessibility callbacks never block binder thread** | KDoc | `offer()` = non-suspending `trySend` onto an UNLIMITED channel; heavy work on `Dispatchers.IO`. No `runBlocking` on callback paths. | **TRUE** | CapturePipeline.kt:61,82-88,103-105 |
| **Accessibility scoped to target pkgs + minimal events** | XML config | `packageNames` = exactly 6 messaging pkgs; only `typeNotificationStateChanged|typeWindowContentChanged`; code re-filters by package; bounded tree depth. | **TRUE** | accessibility_service_config.xml:9,14 |
| **PurgeWorker is WorkManager, not a busy loop** | efficiency claim | `CoroutineWorker`, single `doWork()` per run, scheduled `enqueueUniquePeriodicWork(24h, KEEP, battery-not-low)`. | **TRUE** | PurgeWorker.kt; AppInitializer / AppModule scheduling |
| **Per-condition channels @ IMPORTANCE_HIGH + custom sounds + DND bypass** | alerts claim | One channel per (type×preset×dnd) at `IMPORTANCE_HIGH`; sounds resolved from `res/raw` via `getIdentifier`; `setBypassDnd(rule.dndOverride)`; `ACCESS_NOTIFICATION_POLICY` declared. *(Wiring is real; user must still grant policy access at runtime — no in-app request found.)* | **TRUE** | NotificationChannels.kt; AlertDispatcherImpl.kt:55-61; AndroidManifest.xml:16 |
| **7 sound presets are real resources** | alerts claim | All 7 `preset_*.wav` exist in `res/raw`, 4.8 KB each, real RIFF/WAVE PCM (not zero-byte stubs). Names map 1:1 to `SoundPreset.rawName`. | **TRUE** | res/raw/preset_*.wav; Enums.kt:56-64 |
| **`AlertDispatcher.fire` is wired & called** | alerts claim | The *wiring* is real (Hilt-provided, reachable in source at CapturePipeline.kt:249). It is **unreachable at runtime** only because of the upstream conversation gap — the dispatcher code itself is not dead/fake. | **TRUE (wiring) / unreachable at runtime** | CapturePipeline.kt:247-249; DomainModule.kt:75-81 |
| **Per-app parsers (WA/IG/Messenger/SMS) are real** | parser claim | Real normalization: MessagingStyle iteration, group detection, inline `Sender: text` split, summary-noise filtering, deleted-sentinel tagging. Messenger accepts both `orca` + `katana`. | **TRUE** | WhatsAppParser.kt:107-145; MessengerParser.kt:25-29; SmsParser.kt:62-93 |
| **Customization (icon switcher + theme tokens)** | PRD §6E | Real `setComponentEnabledSetting` alias switching; theme consumes persisted `ThemeSettings`. | **TRUE** | IconSwitcherImpl.kt; Theme.kt; AndroidManifest.xml:63-104 |
| **Onboarding launches real system intents** | onboarding claim | Real `ACTION_NOTIFICATION_LISTENER_SETTINGS` / policy / accessibility intents + real grant detection via `Settings.Secure`. | **TRUE** | OnboardingScreen.kt |
| **SMS/MMS coverage** | PRD §5 / manifest: "Messages (SMS/MMS)", onboarding "Read SMS & MMS" | SMS new-row ingest + deletion-diff genuinely implemented. **MMS content is never read** — `MMS_URI` is registered (fires `onChange`) but every query targets `Telephony.Sms.*`; an inbound MMS just triggers a wasted SMS rescan. | **PARTIAL** | SmsObserver.kt:207,233 vs :95-101,165-171,251-257; OnboardingScreen "Read SMS & MMS" |
| **`CapturePipeline.handleSmsChanged`** | route branch exists | Deliberately empty body (`@Suppress("UNUSED_PARAMETER")`). NOT dishonest: the real SMS work runs in `SmsObserver` via `ingestSms`/`markSmsDeleted`, and the KDoc explains why the bare signal is a no-op. | **PARTIAL (honest no-op)** | CapturePipeline.kt:207-221,227-236 |
| **`resolveConversationId` / `upsertConversation`** | "the data layer creates the conversation before ingesting its first message" | Both are fully written **and have ZERO callers** — dead code. This is the exact wiring that would fix everything; its presence makes the repo *look* complete. | **FALSE (dead code)** | MessageRepositoryImpl.kt:30,117,150 (grep: no call sites) |
| **No literal stub markers** | — | No `TODO`/`FIXME`/`NotImplementedError`/"coming soon" in `com.quietping`. The failure is structural, not a marker. | **TRUE** | full-tree grep |

---

## PER-APP COVERAGE MATRIX

| App (package) | Parser | Registered (manifest + DI + a11y) | Patterns | Triggers working (in isolation) | Triggers working **end-to-end** | Verdict |
|---|---|---|---|---|---|---|
| **WhatsApp** (`com.whatsapp`) | Yes (WhatsAppParser) | Yes — a11y pkg ✓, `@IntoSet` ✓, `WHATSAPP` enum ✓ | Yes — poll/mention/reply/group/deleted regex | All 6 | **None** | **NOT WORKING** |
| **Instagram** (`com.instagram.android`) | Yes (InstagramParser) | Yes ✓ ✓ ✓ | Yes (poll/mention/group; some "Limited" per PRD) | All applicable | **None** | **NOT WORKING** |
| **Messenger** (`com.facebook.orca`) | Yes (MessengerParser) | Yes ✓ ✓ ✓ | Yes | All applicable | **None** | **NOT WORKING** |
| **Facebook** (`com.facebook.katana`) | Yes (MessengerParser also accepts katana) | Yes — a11y pkg ✓, enum `FACEBOOK` ✓ (shares Messenger parser) | Yes (via Messenger sentinels) | All applicable | **None** | **NOT WORKING** |
| **SMS** (`com.android.messaging`) | Yes (SmsParser + `SmsObserver.readNewSms`) | Yes ✓ ✓ ✓ | KEYWORD/NAME/VIP/REPLY(@handle) only — **no** SMS poll/group/mention-sentinel entries | KEYWORD, VIP, NAME(@handle/name), REPLY(@handle) | **None** | **NOT WORKING** |

**Why every app is NOT WORKING (single shared root cause).** All four notification parsers emit `conversationId = 0` (base `NotificationParser.buildMessage`, NotificationParser line 48-50; SmsParser.kt:77,93). The capture pipeline never resolves/creates a real conversation row, so on a real device:
- **(a) No alert ever fires** — `appPackageFor(message)` searches `conversations()` (always empty) for `id == 0`, returns null, and `ingestAndMatch` returns at CapturePipeline.kt:243 **before** `ruleEngine.evaluate` / `alertDispatcher.fire` (lines 247-249).
- **(b) Nothing is archived** — `vaultManager.ingest` → `messageRepository.upsert` inserts `MessageEntity(conversation_id = 0)`; `MessageEntity` has a hard FK to `ConversationEntity` (MessageEntity.kt:22-27) and `PRAGMA foreign_keys = ON` runs on every connection (DatabaseFactory.kt:55), so the insert throws `SQLiteConstraintException`, swallowed at CapturePipeline.kt:84-85.

SMS additionally carries a real `threadId` as `conversationId` (SmsObserver.kt:141), but **no `ConversationEntity` row with that id is ever created either**, so SMS hits the same null-app early-return and the same FK violation. SMS is "closer" but still fully dead end-to-end.

**SMS trigger nuance (fail-closed-by-design, reachable, undocumented):** `PatternCatalog` has no `POLL_SENTINELS` / `GROUP_EVENT_SENTINELS` / `MENTION_SENTINELS` entry for `AppPackage.SMS`, and POLL/GROUP have no fallback path. The UI lets a user create a `POLL_CREATED` or `GROUP_EVENT` rule scoped to SMS (valid with a blank pattern), which then silently never matches. This is sensible (SMS has no poll/group concept) but is a silent no-match path. It is moot today because SMS matching never runs at all.

---

## "Is it lying?" — only findings that survived adversarial refutation

### A. REAL DISHONESTY (claims the code provably cannot deliver)

1. **The headline data-flow promise is false.**
   PRD.md:109-111 states the flow reaches `RuleEngine (match?) → AlertDispatcher (ping) and VaultManager (archive) → Room`. In the live pipeline it reaches **neither** AlertDispatcher nor a successful Room write. `CapturePipeline.kt:243` returns before rule evaluation; `MessageRepositoryImpl.kt:74-77` throws on insert. → **This is the core lie.**

2. **"Every incoming chat message is written to the encrypted store the moment it arrives" is false.**
   PRD.md:196-197. With `conversation_id = 0`, FK enforcement ON (DatabaseFactory.kt:55) + `MessageEntity` FK (MessageEntity.kt:22-27), **every** insert fails and is swallowed. The Vault persists nothing for any source. The "Deleted → Recovered" (PRD.md:199-200) and edit-history promises therefore also cannot surface anything (VaultManagerImpl.kt:128-130 always returns early on an empty `conversations()`).

3. **Conditional alerting ("alerts only when it matters") is non-functional for the 4 chat apps.**
   PRD.md:1,39,117-124 advertise per-app conditional pings. The RuleEngine — though correct in isolation — is **unreachable** for notification-sourced messages (CapturePipeline.kt:243). No ping can ever fire.

4. **Dead code dressed as a complete data layer.**
   `resolveConversationId` / `upsertConversation` (MessageRepositoryImpl.kt:117,150) are fully written but have **zero callers** (grep-confirmed). Their presence makes the repository *read* as a finished Vault store when the create-conversation step is, in practice, never executed. The KDoc at NotificationParser line ~21 and VaultManagerImpl.kt:28-33 even asserts "the data layer resolves the real conversation row before persisting" — a contract that is **not implemented**.

5. **Match-feed integrity claim is false.**
   `MatchLog.messageId` is always 0 (AlertDispatcherImpl.kt:77); the persisted message id is never written back onto `match.message`. Even if a match fired, `MatchLogEntity`'s FK to `messages.id` (MatchLogEntity.kt:17-22) would throw. The audit-log feature (PRD §9.1) is non-functional.

### B. HONESTLY-DISCLOSED LIMITATIONS (NOT lies)

- **Chat apps are notification-only — DISCLOSED.** PRD.md:152: "for these three apps the **notification stream is the [only source]**." The app does **not** pretend to read WhatsApp/IG/Messenger databases. This commonly-suspected "lie" is explicitly documented and is **not** dishonest.
- **No media/voice, truncated previews — DISCLOSED.** PRD.md:144-147,207: "notification path recovers text/captions only (no media/voice)". Matches the parsers.
- **Locale/format fragility — DISCLOSED.** PRD.md:126-131 openly states detection depends on notification text format and degrades gracefully.
- **DND bypass needs runtime grant.** The manifest permission alone doesn't grant policy access; the code comment honestly notes bypass is only effective if the user grants it. (Wiring is real; just incomplete onboarding — not a lie about the wiring.)
- **`handleSmsChanged` empty body.** A real no-op, but documented and compensated by `SmsObserver` — honest, not concealment.

### C. PARTIAL / OVERSTATED (advertised broader than implemented)

- **MMS.** PRD §5, manifest comment (AndroidManifest.xml:11), and onboarding "Read SMS & MMS" all advertise MMS. **MMS content is never read** (SmsObserver.kt:207,233 register the URI but every query is SMS-only). An MMS triggers a wasted SMS rescan and is otherwise dropped. This is an **overstatement**, milder than the core lie because SMS itself is substantively implemented (and, separately, dead end-to-end for the same conversation-row reason).

---

## What would make it real (single minimal fix)

In `CapturePipeline` (both the notification and accessibility paths, and the SMS path), before `vaultManager.ingest(message)`:
1. derive a `conversationKey` from `RawEvent.key` + title/subText (or the SMS `threadId`),
2. call `MessageRepositoryImpl.resolveConversationId(app, key, displayName, isGroup)` to create/look up the row,
3. set `message.conversationId` to that resolved id,
4. scope rule evaluation off the **resolved** `app` instead of `appPackageFor()`,
5. write the persisted message id back onto the `MatchResult` so `MatchLog` (and notification ids) are correct.

This is one wiring change against helpers that already exist. Everything downstream (RuleEngine, dispatcher, channels, Vault dedupe/version, deletion diffing) is already correct and would light up.

---

## Counts

- **Apps fully wired & working end-to-end:** 0
- **Apps partial:** 0
- **Apps not working end-to-end:** 5 (WhatsApp, Instagram, Messenger, Facebook, SMS)
- **Confirmed lies (real dishonesty, survived refutation):** 5
  1. End-to-end data-flow promise (PRD.md:109-111)
  2. "Every message written the moment it arrives" / Vault capture (PRD.md:196-197)
  3. Conditional alerting for the 4 chat apps (PRD.md:1,117-124)
  4. Dead-code "complete data layer" / unimplemented conversation-resolution contract (MessageRepositoryImpl.kt:117,150; NotificationParser KDoc)
  5. Match-feed/audit-log integrity (AlertDispatcherImpl.kt:77; MatchLogEntity.kt:17-22)
  *(MMS overstatement and the chat-notification-only/no-media items are tracked separately as PARTIAL / honestly-disclosed, not counted as lies.)*

**Final verdict:** The code is well-crafted at the unit level but **does not work for any of the five targets end-to-end**, and the PRD **lies** about the end-to-end capture flow, the "every message archived instantly" Vault, conditional alerting, and the audit log — all blocked by one missing conversation-row integration plus FK enforcement. The notification-only constraint for chat apps is **honestly disclosed and is not a lie**.
