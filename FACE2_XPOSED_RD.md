# FACE2_XPOSED_RD — Requirements & Design Document

**Component:** Face 2 — the Xposed/LSPosed module half of QuietPing.
**Goal:** Show messages that a chat app deleted ("delete for everyone" / revoke / unsend)
**inside that app's own native chat UI**, on a rooted device, gated per-app from QuietPing.
**Status:** Scaffold. The obfuscated per-app hook point is a documented TODO (needs per-version RE).

> This document covers Face 2 ONLY (the in-process hook). Face 1 (the manager UI + the
> toggle that writes the per-app gate) is referenced where the two meet, but its
> implementation is out of scope here.

---

## 1. Why a second face exists

QuietPing's Face 1 is a normal sandboxed app: it observes chat apps *from the outside*
(notification listener, accessibility, SMS/MMS, media). It can archive a deleted message
into its **own** vault, but it can never draw into WhatsApp's real chat screen — the OS
sandbox forbids one app from touching another app's process, memory, or views.

The only way to render inside the real chat UI is to run code **inside the target app's
own process**. On stock Android that is impossible. On a rooted device with the
**LSPosed** framework (Zygisk + Magisk), code can be injected at process spawn (Zygote).
Face 2 is that injected code.

```
┌──────────────── QuietPing APK (one install) ─────────────────┐
│  FACE 1  Manager (sandboxed, what the user taps)             │
│    per-app toggle  ──writes──►  XSharedPreferences gate      │
│                                                              │
│  FACE 2  Xposed module (injected into target by LSPosed)    │
│    handleLoadPackage(target)                                 │
│      └─ read gate ── off ─► no-op (zero footprint)           │
│                    ── on  ─► hook revoke handler             │
│                              └─ keep/mark the deleted msg    │
│                                 target's own renderer draws  │
│                                 it  → TRULY in-chat          │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. Hard prerequisites (Face 2 cannot create these)

| Prereq | Detected by | If missing |
|--------|-------------|------------|
| Root (Magisk) | Face 1 `RootManager` | feature greyed out; capture falls back to Face 1 observers |
| Zygisk + LSPosed framework | LSPosed must be flashed by user | Face 2 is simply never loaded; app still works as a normal observer |
| Module enabled + scoped in LSPosed | user / `xposedscope` hint | hook never runs |

Face 2 **rides on top of** LSPosed. It does NOT implement its own injector (that would be
re-building Xposed — out of scope, and unsafe).

---

## 3. Invariant compliance (CLAUDE.md)

- ✅ **No INTERNET.** Hooks need zero network. Xposed API is `compileOnly` → not in the APK,
  not a runtime networking dep. The no-INTERNET guarantee is untouched.
- ✅ **Encrypted at rest.** Any text Face 2 recovers is handed to the existing SQLCipher
  vault path; nothing is written to plain logs or files.
- ✅ **Event-driven.** Hooks fire on the target's own revoke callback — no polling.
- ✅ **Scoped.** Module scope is fixed to exactly the target packages (§5), mirroring the
  "accessibility is opt-in and filtered to target packages" invariant.
- ⚠️ **New axis: root + LSPosed.** App stops being install-and-go; Play Store distribution
  is already N/A (GitHub sideload). Non-rooted users MUST get a graceful "unavailable",
  never a crash. Face 2's absence is silent by construction (LSPosed just never loads it).

---

## 4. Targets & per-app strategy

| App | Package(s) | Delete mechanism | Face 2 strategy | This scaffold |
|-----|-----------|------------------|-----------------|---------------|
| WhatsApp | `com.whatsapp`, `com.whatsapp.w4b` | "revoke" protocol msg | block-revoke: skip the delete, mark bubble | **working hook point stub** |
| Instagram | `com.instagram.android` | "unsend" | TODO (different internal model) | scoped, not yet hooked |
| Facebook / Messenger | `com.facebook.orca`, `com.facebook.katana` | "remove for everyone" | TODO | scoped, not yet hooked |

**Block-revoke** is the chosen WhatsApp strategy: intercept the handler that processes an
incoming revoke and **skip the deletion**, optionally flagging the message as
`revoked-but-kept`. WhatsApp's own renderer then draws the original bubble → genuinely
in-chat, minimal surface, most reliable.

### 4.1 The obfuscation problem (the real cost)

WhatsApp ships obfuscated: class/method names are `a.b.c` and **change almost every
update**. You cannot hook by name. The durable approach:

1. Pull the *current* target APK, decompile (`jadx`).
2. Locate the revoke handler by **behavioural signature** (the method that, given a
   protocol message of the revoke type, marks a row deleted), not by name.
3. Encode a **bytecode/shape matcher** so the hook re-finds it after each update.
4. Fail safe: if the matcher finds nothing, **no-op** (never crash the host app).

This per-version RE is the recurring maintenance cost and is intentionally a TODO in the
scaffold — it cannot be hardcoded.

---

## 5. Module manifest contract

Declared in `AndroidManifest.xml` under `<application>`:

| meta-data / resource | Value | Purpose |
|----------------------|-------|---------|
| `xposedmodule` | `true` | marks the APK as an Xposed module |
| `xposeddescription` | `@string/xposed_description` | shown in LSPosed manager |
| `xposedminversion` | `93` | LSPosed (modern Zygisk) baseline |
| `xposedscope` (array) | `@array/xposed_scope` | LSPosed pre-suggests these packages only |
| asset `assets/xposed_init` | entry class FQN | LSPosed entry point list |

Scope array = the target packages in §4, and **nothing else**.

---

## 6. The per-app gate (Face 1 ⇄ Face 2 bridge)

Face 1 and Face 2 run in **different processes** (manager vs. inside WhatsApp), so a normal
`SharedPreferences` can't be shared. Bridge = **`XSharedPreferences`**:

- Prefs file: `quietping_xposed` (world-readable; LSPosed supports this for modules).
- Keys: `hook_enabled__<package>` → `Boolean`. e.g. `hook_enabled__com.whatsapp`.
- **Write side (Face 1):** the per-app "Deep capture (root)" toggle. *(out of scope here)*
- **Read side (Face 2):** on `handleLoadPackage`, read the gate for `lpparam.packageName`;
  if `false`/absent → return immediately, zero footprint.

`XposedGate` (this scaffold) owns the file name + key convention so both faces agree.

---

## 7. Files in this scaffold

```
gradle/libs.versions.toml          + xposed api version & library (compileOnly artifact)
settings.gradle.kts                + maven { api.xposed.info } repo
app/build.gradle.kts               + compileOnly(libs.xposed.api)
app/src/main/AndroidManifest.xml   + xposed meta-data (module/description/minversion/scope)
app/src/main/res/values/strings.xml  + xposed_description
app/src/main/res/values/arrays.xml   (new) xposed_scope array
app/src/main/assets/xposed_init      (new) entry-class FQN
com/quietping/xposed/XposedGate.kt          gate constants + XSharedPreferences read
com/quietping/xposed/QuietPingXposedModule.kt  IXposedHookLoadPackage entry, dispatch by package
com/quietping/xposed/WhatsAppRevokeHook.kt     block-revoke hook + RE TODO
```

---

## 8. Verification

- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17` (repo builds ONLY on JDK17 — lessons.md).
- `./gradlew :app:compileDebugKotlin` must pass.
- `:app:assembleDebug` must pass and the APK must still declare **NO INTERNET** and **NOT**
  bundle `de.robv.android.xposed.*` (compileOnly guarantees this — verify with `unzip -l`).
- Runtime activation (rooted device + LSPosed) is a manual user step, documented, not CI.

---

## 9. Out of scope / follow-ups

- Face 1 manager UI: root + LSPosed detection (`RootManager`), per-app toggle, gate writer.
- Instagram / Facebook hook points.
- Recovering text into the SQLCipher vault from the hook (bridge back to Face 1).
- The WhatsApp revoke-handler bytecode matcher (per-version RE workflow).
