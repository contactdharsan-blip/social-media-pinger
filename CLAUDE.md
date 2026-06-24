# CLAUDE.md — QuietPing

On-device conditional alert filter for Android (Kotlin · Compose · Clean Architecture + MVVM).
Read `PRD.md` for product spec, `INSTALL.md` for setup. This file is the working contract.

> Maintenance rule (Boris): anytime Claude does something wrong here, add a line so it
> doesn't repeat. Fix it in this file or a skill — not just in the current chat.

---

## Build & verify (run before claiming done)

**Use the Gradle wrapper `./gradlew`, never a globally-installed `gradle`.**

```bash
# 1. Compile (fast feedback)
./gradlew :app:compileDebugKotlin

# 2. Unit tests — domain logic lives in app/src/test
./gradlew :app:testDebugUnitTest

# Single test class
./gradlew :app:testDebugUnitTest --tests "com.quietping.domain.rules.RuleEngineImplTest"

# 3. Full assemble before a PR
./gradlew :app:assembleDebug

# 4. Lint
./gradlew :app:lintDebug
```

A change is **not done** until the relevant command above passes. Quote the failing
output if it doesn't — don't claim success on an unverified build.

---

## Hard invariants — never violate

These define the product. Breaking one silently breaks the privacy promise.

- **No `INTERNET` permission.** Never add it to `AndroidManifest.xml`, never add a
  networking dependency. Zero exfiltration is OS-guaranteed and must stay that way.
- **Fully on-device.** No servers, no analytics, no cloud sync, no telemetry.
- **Encrypted at rest.** All persistence goes through SQLCipher Room + Keystore-wrapped
  key (`data/db/DbKey.kt`, `DatabaseFactory.kt`). Never write message content to plain
  storage, logs, or DataStore.
- **Event-driven only.** Capture is listener/observer callbacks — no polling loops.
- **Capture threads never block.** Service/binder callbacks normalize to `RawEvent` and
  hand off to the ingestion `Channel` on `Dispatchers.IO` immediately.
- **Accessibility is opt-in** and filtered to the 4 target packages only.

---

## Architecture map

Single-Activity Compose app, unidirectional data flow. Package root: `com.quietping`.

```
capture/   Android services → normalize to RawEvent → ingestion Channel
domain/    Parsers (per-app) · RuleEngine (Aho-Corasick) · VaultManager · AlertDispatcher
data/      Room (SQLCipher) DAOs/entities · DataStore · Repository impls (return Flow)
ui/        Compose screens + ViewModels (StateFlow); theme/ holds LiquidGlass tokens
di/        Hilt modules (App/Database/Domain/Repository) + AppInitializer
work/      WorkManager (retention purge / digest)
icon/      app-icon switcher (activity-alias toggling)
```

Flow: notification/SMS row → capture → `RawEvent` → parser → domain `Message` →
`RuleEngine` (match?) → `AlertDispatcher` (ping) **and** `VaultManager` (archive) → Room → UI.

---

## How to work here (behavioral)

**Think before coding (Karpathy).**
- State assumptions explicitly instead of proceeding silently. If multiple readings
  exist, surface them — don't pick one quietly.
- For multi-step work, write a short plan with verification steps before editing.

**Simplicity first.**
- Write the minimum code that satisfies the request. No speculative features, no
  unasked-for abstraction or configurability. If 200 lines could be 50, write 50.

**Surgical changes.**
- Edit only what the task needs. Don't reformat, rename, or "improve" adjacent code,
  comments, or imports. Remove only what your change made dead.
- Match the style of the file you're in.

---

## Project conventions

- **Kotlin + Coroutines/Flow.** Repositories return `Flow`; ViewModels expose
  `StateFlow`. DAOs are reactive.
- **DI is Hilt + KSP.** New bindings go in the matching `di/*Module.kt`. Annotate
  injectable Android entry points; constructor-inject elsewhere.
- **Dependencies are version-cataloged.** Add coordinates to
  `gradle/libs.versions.toml` and reference via `libs.*` — never hardcode a version in
  a `build.gradle.kts`.
- **JVM 17**, `minSdk 26`, `compileSdk`/`targetSdk 34`, namespace `com.quietping`.
- **Domain logic is unit-tested** (Truth + coroutines-test). New parser patterns, rule
  matching, vault dedupe/versioning → add a test under `app/src/test`.
- **Parsing is locale-fragile** — detection keys off notification text format. Put new
  patterns in the versioned `domain/parser/PatternCatalog.kt`, cover with a test, and
  fail safe (no false ping) rather than guessing.
- **Theming** uses DESIGN.md tokens via `ui/theme/` (`Theme.kt`, `Color.kt`, `Glass.kt`).
  Honor reduced-motion; degrade glass blur on low-end devices.

---

## Git

- Commit/push as the user's own git identity (`git config user.email`) — never override it.
- Branch off `main`; commit/push only when asked.
- Build + tests green before opening a PR.

---

## Permissions touched (Play-sensitive)

`BIND_NOTIFICATION_LISTENER_SERVICE`, `READ_SMS` (restricted — needs declaration form),
`POST_NOTIFICATIONS`, `ACCESS_NOTIFICATION_POLICY`, `RECEIVE_BOOT_COMPLETED`, optional
`BIND_ACCESSIBILITY_SERVICE`. Adding/changing any of these carries Play-review risk —
flag it, don't do it silently.
