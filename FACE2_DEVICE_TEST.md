# FACE2_DEVICE_TEST — on-device validation checklist

Face 2 (root + Xposed deep capture) cannot be validated in CI — it needs a **rooted device with
LSPosed** and the **real** target apps. This is the manual procedure. It also doubles as the RE
step that turns the observe-only SQLite strategies into active block-revoke.

## Prerequisites
- [ ] Device rooted with **Magisk** + **Zygisk** enabled.
- [ ] **LSPosed** flashed and active.
- [ ] QuietPing debug APK installed (`./gradlew :app:assembleDebug`, JDK17).
- [ ] A throwaway / secondary account on the target app (ban risk — never your primary).

## A. Module loads and is gated
1. [ ] LSPosed manager → Modules → **QuietPing** appears as a module (reads the `xposedmodule`
       meta-data). Enable it.
2. [ ] Scope: only the 5 target packages are suggested (from `@array/xposed_scope`). Tick the app
       under test. Force-stop that app so LSPosed reloads it.
3. [ ] In QuietPing → Privacy → **Deep capture (root)**: the screen shows the per-app toggles
       (RootManager detected root + LSPosed). On a non-rooted build it must instead show the
       "unavailable" explainer — verify that on a stock device too.
4. [ ] Enable the toggle for the app under test. Confirm the gate file was written:
       `adb shell run-as`/`su` → `cat /data/data/com.quietping/shared_prefs/quietping_xposed.xml`
       shows `hook_enabled__<pkg>=true` and a `bridge_token`.

## B. Hook attaches (observe-only)
5. [ ] `adb logcat | grep QuietPing` while opening a chat in the target app. Expect:
       `QuietPing: <App> SQLite write hook attached (validated=false).`
6. [ ] From the *other* account, send a message then **delete for everyone** / **unsend** /
       **remove for everyone**. In logcat, expect an OBSERVE line:
       `QuietPing: OBSERVE <App> candidate UPDATE/DELETE table='…' cols=[…] where='…'`.
7. [ ] Record the exact `table` and `cols` (and for WhatsApp, which column flips on revoke). This is
       the evidence to finish the per-version detector. Confirm the app still behaves 100% normally
       (observe-only mutates nothing).

## C. Turn on blocking (only after B confirms the shape)
8. [ ] WhatsApp: tighten `WhatsAppDbRevokeStrategy.looksLikeRevoke` to the confirmed column/value,
       then flip `REVOKE_SCHEMA_VALIDATED = true`. IG/FB: set the `validated` arg true once their
       DELETE shape is confirmed and the detector narrowed (it currently matches *any* DELETE on the
       message table — DO NOT enable blocking until narrowed, or you'll lose normal deletions).
9. [ ] Rebuild, repeat B6. Expect `QuietPing: BLOCKED <App> …` and the deleted message **stays
       visible in the real chat UI**. This is the feature.

## D. Vault bridge (optional, once the precise handler recovers text)
10. [ ] When the obfuscated-handler path (or a pre-read) yields the original body, it calls
        `VaultBridge.send(...)` → `DeepCaptureProvider`. Verify the message appears in QuietPing's
        Vault. Spoof check: from a NON-target app, call `content://com.quietping.deepcapture` →
        must be rejected (`uid=… not a target app` in logcat). The auth boundary is caller UID, not
        a token.
11. [ ] Confirm encrypted-at-rest: the recovered text must NOT appear in any plaintext file; only in
        the SQLCipher DB. Spot-check `strings` over the app's databases dir shows nothing readable.

## Fail-safe regression (must always hold)
- [ ] Disable the QuietPing module in LSPosed → target app behaves exactly as stock.
- [ ] Non-rooted device → app installs, runs, deep-capture screen shows "unavailable", no crash.
- [ ] Hook attach failure (any) → logcat notes it and the target app is unaffected.
