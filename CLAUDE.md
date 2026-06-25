# CLAUDE.md — QuietPing

On-device conditional alert filter + deleted-message vault for Android
(Kotlin · Compose · Clean Architecture + MVVM).
Read `PRD.md` for product spec, `INSTALL.md` for setup, `FACE2_XPOSED_RD.md` for the
rooted deep-capture design. This file is the working contract.

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

**JDK 17 only** (AGP 8.7.3). The machine `java` may be 8/11 — set `JAVA_HOME` first and
verify with `/usr/libexec/java_home -V`. It's `/opt/homebrew/opt/openjdk@17` on some
machines, the Android Studio JBR (`…/Android Studio.app/Contents/jbr/Contents/Home`) on
others — don't trust a hard-coded path from memory. The first build after adding a dep
must resolve **online** (drop `--offline`): the UI/effects libs and the Xposed API
(`api.xposed.info`) aren't cached yet.

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
- **Accessibility is opt-in** and filtered to the 4 target chat packages only.
- **Face 2 (Xposed) ships inert.** The LSPosed module + root deep-capture do nothing
  unless the device is rooted with LSPosed AND the user enables and scopes them. Scope is
  fixed to the same chat packages (`xposed/XposedGate.kt`) — never widen it.
- **Xposed API is `compileOnly`** and must NEVER ship in the APK — the framework provides
  `de.robv.android.xposed.*` at runtime. Verify by inspecting the DEX (`dexdump`), not
  `unzip -l`: app classes live in `classes*.dex`, so a zip listing sees nothing. Expect
  type *references*, zero *defined* `de.robv` classes.
- **Cross-UID bridge auths by kernel UID.** The Face 2 → vault hand-off
  (`capture/DeepCaptureProvider.call()`) authenticates via `Binder.getCallingUid()` →
  `getPackagesForUid()` against the allowlist. Never gate it on a shared secret — Xposed
  prefs are world-readable, so a secret there is spoofable.

---

## Architecture map

Single-Activity Compose app, unidirectional data flow. Package root: `com.quietping`.

**Two faces, one APK** (Face 2 is inert until opted in — see invariants):
- **Face 1 — universal.** Notification listener + SMS observer. Any phone, no root. The
  primary product.
- **Face 2 — deep capture (rooted only).** An LSPosed module (`xposed/`) hooks the chat
  apps *in their own process* to recover "delete-for-everyone" messages, bridged back to
  Face 1's vault via `DeepCaptureProvider`. See `FACE2_XPOSED_RD.md`.

```
capture/   notification listener + SMS observer → normalize to RawEvent → ingestion
           Channel · DeepCaptureProvider = cross-UID sink for Face 2 deliveries
domain/    parser/ (per-app + versioned PatternCatalog) · rules/ (RuleEngine, Aho-Corasick) ·
           vault/ · alerts/ (AlertDispatcher + RepeatSenderTracker) · security/ (PIN hash,
           decoy mode) · settings/ · icon/ (alias model)
data/      Room (SQLCipher) DAOs/entities · DataStore · repo impls (return Flow) —
           messages, media vault, break-in log, root/deep-capture gate
xposed/    Face 2 LSPosed module + per-app unsend hooks + VaultBridge (compileOnly API)
root/      RootManager — su detection / root gate for Face 2
ui/        Compose screens + ViewModels (StateFlow); theme/ = LiquidGlass tokens + Haze blur
di/        Hilt modules (App/Database/Domain/Repository) + AppInitializer
work/      WorkManager: PurgeWorker (retention) · DigestWorker (daily) · OtpCleanupWorker
icon/      app-icon switcher (activity-alias toggling)
```

Flow: notification/SMS row → capture → `RawEvent` → parser → domain `Message` →
`RuleEngine` (match?) → `AlertDispatcher` (ping) **and** `VaultManager` (archive) → Room → UI.
Face 2: a hook intercepts an unsend inside the chat app → `VaultBridge` →
`DeepCaptureProvider.call()` → same vault path.

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
- **Add-on UI libs are pinned to the Compose 1.7.x line** — Haze, compose-shimmer, Coil3
  (core only, never `coil-network-*`), Telephoto. Before bumping any, fetch its
  `.module`/`.pom` and check its `androidx.compose`/`androidx.activity` floor: "latest"
  pulls JetBrains-Compose 1.8+ → demands compileSdk 36 / AGP 8.9 and fails the build.
- **Room is destructive-migration for capture data.** Adding a column/entity → bump
  `@Database(version=…)`; the store is a rebuildable, retention-purged cache, so
  `.fallbackToDestructiveMigration()` in `DatabaseFactory` is fine — don't hand-write
  SQLCipher ALTERs.
- **Widen a shared interface → update its test fakes.** Adding a method to a repository
  interface breaks every hand-rolled `Fake*` in `app/src/test`; grep and add the override
  before running `:app:testDebugUnitTest`.

---

## Git

- Commit/push as the user's own git identity (`git config user.email`) — never override it.
- Branch off `main`; commit/push only when asked.
- Build + tests green before opening a PR.

---

## Permissions touched (Play-sensitive)

`BIND_NOTIFICATION_LISTENER_SERVICE`, `READ_SMS` (restricted — needs declaration form),
`POST_NOTIFICATIONS`, `ACCESS_NOTIFICATION_POLICY`, `USE_FULL_SCREEN_INTENT`,
`RECEIVE_BOOT_COMPLETED`, `USE_BIOMETRIC`, `FOREGROUND_SERVICE(_SPECIAL_USE)`,
`READ_MEDIA_IMAGES/VIDEO/AUDIO` (+ `READ_EXTERNAL_STORAGE` ≤ API 32, for the media vault),
optional `BIND_ACCESSIBILITY_SERVICE`. The Xposed module metadata (`xposedmodule`,
`xposedscope`) is also Play-sensitive. Adding/changing any of these carries Play-review
risk — flag it, don't do it silently.
