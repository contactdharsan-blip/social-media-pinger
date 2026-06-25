# WHATSAPP_REPACKAGE_PLAN — modified-APK route (DESIGN ONLY, manual)

> **Scope of this document.** This describes the repackaged-APK ("GBWhatsApp-style") approach for a
> user's OWN device and OWN account. QuietPing does **not** automate it — there is no auto-pull /
> patch / re-sign / silent-install pipeline in the app, by deliberate decision (see §7). This is a
> manual procedure + a precise statement of why it is worse than the Xposed module already built
> (`FACE2_XPOSED_RD.md`). Read §6 (risks) before doing any of it.

---

## 1. Target

- **Primary:** WhatsApp (`com.whatsapp`). Anti-revoke is best understood there.
- Instagram (`com.instagram.android`) and Messenger/Facebook (`com.facebook.orca` / `.katana`) are
  analogous but use server-driven stores (unsend / remove-for-everyone), so a local APK patch is
  weaker for them than for WhatsApp. This plan focuses on WhatsApp.

## 2. The modification

Goal = **anti-revoke**: when a "delete for everyone" arrives, do not remove/replace the message —
keep the original bubble (optionally tag it "deleted").

Patch points in WhatsApp's decompiled smali (names are obfuscated and rotate every version — found
by behavioural anchors, NOT fixed names; same RE problem as `WHATSAPP_RE_PROCEDURE.md`):

1. **Revoke handler** — the method that, given a revoke protocol message, marks the message row
   deleted. Patch: make it a no-op for received messages (early `return-void`).
2. **Revoke renderer** — the code that swaps the bubble text for "This message was deleted". Patch:
   skip the swap so the original text renders.
3. (Optional) **a "deleted" tag** — inject a marker so you can tell it was revoked.

This is the SAME logical change the Xposed module does at runtime — except baked permanently into
the binary instead of hooked in memory.

## 3. Toolchain (desktop — NOT on-device)

| Tool | Use |
|------|-----|
| `apktool d` | decode APK → smali + resources |
| (edit smali) | apply §2 patches |
| `apktool b` | rebuild → unsigned APK |
| `zipalign -p 4` | align |
| `apksigner sign` | **re-sign with YOUR key** (WhatsApp's key is not available) |

On-device patching (running apktool/smali inside Android) is technically possible but heavy,
fragile, and pointless — do it on a desktop.

## 4. Install mechanism

- The re-signed APK has a **different signature** than Play's WhatsApp, so it **cannot update over
  it**. You must **uninstall the real WhatsApp first** → see §6 data loss.
- Install: `adb install patched.apk`, or on-device with root `su -c "pm install -r patched.apk"`.
- No root strictly needed to *install* (user sideload works), but you lose the ability to keep the
  original app, and silent install needs root.

## 5. Intended outcome

Deleted ("delete for everyone") messages stay visible inside WhatsApp's own UI, permanently, with no
runtime framework — because the binary itself no longer honours revoke.

## 6. Risks — read before doing anything

1. **Account ban (high, often permanent).** WhatsApp runs tamper + integrity checks (Play Integrity,
   internal signature/attestation). A re-signed client is detected → ban. This is the documented
   GB/FMWhatsApp reality. Use a throwaway number, never your primary.
2. **Data loss.** Uninstalling the real WhatsApp to install the re-signed one drops local chat
   history unless you export/migrate first. Signature mismatch = no in-place upgrade.
3. **Legal.** Decompiling, modifying, and especially **redistributing** WhatsApp's APK violates its
   ToS and copyright/DMCA. Keep any patched build strictly local to your own device; do not share it.
4. **Per-version churn.** Every WhatsApp update = re-decompile, re-find the (rotated) patch points,
   re-patch, re-sign, re-install. Far heavier than the runtime hook.
5. **No OTA updates.** A sideloaded re-signed app won't auto-update from Play; you maintain it.

## 7. QuietPing's role (and what it will NOT do)

QuietPing will **not** ship an automated pull→patch→re-sign→install pipeline. That would be a
copyright-circumvention + forged-signature installer, and it bundles every risk in §6 into one tap.

The most QuietPing could legitimately do (NOT built, would need explicit decision):
- Detect that a patched build is installed and surface status.
- Link to this document / guide the manual steps.
- It must never silently install a forged-signature proprietary APK.

## 8. Recommendation

Use the **Xposed module** (`FACE2_XPOSED_RD.md`) instead. It reaches the SAME outcome — modifying
WhatsApp's behaviour — but:
- keeps the real, signed WhatsApp (no data loss, no re-sign),
- much lower ban surface (runtime hook vs tampered binary),
- reversible (disable the module),
- no redistribution / copyright issue.

The repackage route in this doc is strictly worse on every axis except "no framework needed", and
that single benefit is not worth the bans + data loss + maintenance.
