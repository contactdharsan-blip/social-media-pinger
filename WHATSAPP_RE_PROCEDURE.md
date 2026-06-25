# WHATSAPP_RE_PROCEDURE — finding & wiring the revoke handler

How to locate WhatsApp's "delete for everyone" (revoke) handler for a **specific** version and
fill in the per-version predicates in
`app/src/main/java/com/quietping/xposed/WhatsAppRevokeHook.kt`.

> **This is recurring maintenance, not a one-time task.** WhatsApp re-obfuscates almost every
> release: class and method names (`a.b.c`) rotate. You re-run this procedure each time you bump
> the supported version. The matcher framework
> (`app/src/main/java/com/quietping/xposed/WhatsAppRevokeMatcher.kt`) is version-agnostic — only
> the two inputs in `findRevokeHandler` change.

> **Fail-safe is non-negotiable.** Match by *behavioural shape*, never by name. If you are unsure,
> leave the predicate non-matching: a missed hook keeps WhatsApp 100% functional; a wrong hook can
> corrupt the host. Empty `candidateClassNames` or the `paramCount = -1` sentinel both yield
> `null` and an inert hook — that is the correct default until you have *verified* a match against
> a real APK.

---

## 0. Prerequisites

- A copy of the **exact** target APK (the version users run). Pull from a device:
  `adb shell pm path com.whatsapp` then `adb pull <path>`. WhatsApp ships split APKs — pull the
  base.
- [jadx](https://github.com/skylot/jadx) (GUI `jadx-gui` recommended for xref navigation).
- The on-device build's `versionName`/`versionCode` recorded next to your findings, so the next
  maintainer knows which version the predicates were verified against.

---

## 1. Decompile

```bash
jadx-gui WhatsApp-<version>.apk
```

Let it finish; obfuscated names will be like `X.0a1`, `com.whatsapp.X.b`, etc.

---

## 2. Locate the candidate class(es) → fill `candidateClassNames`

Goal: the class that *processes an incoming revoke protocol message and marks the chat row
deleted*. Approaches, in order of reliability:

1. **String anchors.** jadx-gui → *Search* (Text) for stable, user-visible or protocol strings
   near revoke handling, e.g. `"revoke"`, `"This message was deleted"`,
   `"protocol"`/`"protocolMessage"`, `REVOKE` enum constants, FMessage/`FMessageKey` references.
   These survive obfuscation because they are protocol/string literals, not identifiers.
2. **Protobuf type.** WhatsApp's protocol messages map to a `ProtocolMessage` / `Message.Type`
   with a `REVOKE` (or `7`) variant. Find where that enum value is switched on — the branch that
   handles `REVOKE` calls (or is) the deletion path.
3. **Xref the message store.** From the message-store / DB-row deletion method, walk callers
   upward until you reach the method that receives the revoke protocol message.

Record the **fully-qualified obfuscated class name(s)** that host the handler and put them in:

```kotlin
val candidateClassNames = listOf(
    "X.0aB",            // <- obfuscated FQN(s) from jadx for THIS version
)
```

Keep this list **small and targeted** — `firstMatch` scans every declared method of each class.
Do not brute-force the whole APK.

---

## 3. Derive the behavioural signature → fill the predicate

Open the handler method in jadx and read its decompiled signature + body. Translate its *shape*
(NOT its name) into predicates. The shape is dictated by WhatsApp's protocol model and is far
stabler than identifiers.

Capture, from the decompiled method:

| Observation in jadx | Predicate to set |
|---------------------|------------------|
| Return type (e.g. `void`, `boolean`) | `matchByParamShape(returnType = Void.TYPE / java.lang.Boolean.TYPE)` |
| Number of declared parameters | `matchByParamShape(paramCount = <n>)` |
| Each parameter's role (the revoke protocol message; the chat-row / message-store) | one `ParamPredicate` per index, checking **shape** |
| Instance vs. static; non-synthetic | `matchByModifiers(mustHave = ..., excludeSynthetic = true)` |

**Write `ParamPredicate`s by shape, not name.** Good shape signals on a parameter's `Class<*>`:

- `type.interfaces` — does it implement the protocol-message interface? (find that interface the
  same way, by its own shape/strings.)
- `type.superclass` — extends the FMessage / protocol base?
- `type.declaredFields.size` / `type.declaredMethods.size` — coarse structural fingerprint.
- `Modifier.isAbstract(type.modifiers)`, `type.isEnum`, `type.isInterface`.

Avoid `type.name == "X.0aB"` — the name rotates next release and silently breaks the match.

Example (illustrative — replace every value with what jadx shows for the real version):

```kotlin
val predicate = matcher.allOf(
    matcher.matchByModifiers(excludeSynthetic = true),
    matcher.matchByParamShape(
        returnType = Void.TYPE,
        paramCount = 2,
        paramTypePredicates = listOf(
            ParamPredicate { it.interfaces.any { i -> i.declaredMethods.size in 4..8 } }, // revoke msg
            ParamPredicate { true },                                                       // store: wildcard
        ),
    ),
)
```

Then set the real `candidateClassNames` and remove the `paramCount = -1` sentinel.

---

## 4. Verify against the real APK BEFORE shipping

`firstMatch` logs `QuietPing: revoke matcher resolved <class>#<method>` when it hits. On a rooted
device with LSPosed + the module enabled and the per-app gate on:

```bash
adb logcat | grep QuietPing
```

- **No "resolved" line** → predicate matched nothing; hook is inert (safe). Re-check the shape.
- **A "resolved" line** → confirm it names the method you found in jadx. If it resolves the wrong
  method, tighten the predicates (add a modifier/param-shape constraint) — never loosen until it
  grabs whatever.

Only once the log names the **correct** method do you wire the actual hook in
`WhatsAppRevokeHook.install` (the `XposedHelpers.findAndHookMethod(...)` TODO already in that
file), set `param.result = null` in `beforeHookedMethod` to block the revoke, and re-test that the
deleted bubble stays and WhatsApp is otherwise normal.

---

## 5. Per-version maintenance checklist

- [ ] New WhatsApp version pulled; `versionName`/`versionCode` noted.
- [ ] `candidateClassNames` re-located via §2 (old names are almost certainly stale).
- [ ] Predicate shape re-confirmed via §3 (param count / types can shift with protocol changes).
- [ ] `paramCount = -1` sentinel removed; real values in.
- [ ] Logcat shows the matcher resolving the **correct** method (§4).
- [ ] Hook wired, block-revoke verified in-app, WhatsApp otherwise unaffected.
- [ ] If anything is uncertain, leave it inert (null) and ship safe.
