# Competitive Gap Analysis — QuietPing

What feature-comparable apps do that QuietPing doesn't yet, mapped to scope and
flagged for permission/architecture cost. Prioritized as a backlog, not a wishlist.

> **Date:** 2026-06-25 · Compared against the categories QuietPing competes in.

---

## Apps compared (by category)

| Category | Representative apps | What they're known for |
|---|---|---|
| Deleted-message recovery | **WAMR**, SKIT, Notif Log, "Get Deleted Messages" | Caches notifications + media before deletion; status saver; auto-reply |
| Notification filter / automation | **BuzzKill**, Notification Filter, **Tasker**, MacroDroid | Compound conditions, schedules, per-contact rules, action chaining |
| Quiet-hours / focus | Daywise, Unpluq, system DND | Time-windowed muting, priority-sender override during focus |
| Privacy messengers | **Signal**, Molly | Disappearing msgs, screen security, incognito keyboard, duress |
| Notification history loggers | Notisave, Notification History Log | Full searchable archive, per-app filtering, export |

**Already at parity or ahead** (don't re-list as gaps): conditional re-notify,
deleted/edited vault, decoy PIN, break-in log, screenshot block, hide-notif-content,
retention auto-purge, OTP cleanup, daily digest, per-condition sound presets, icon
disguise, rooted deep-capture (Face 2). QuietPing's privacy posture (no `INTERNET`)
already **beats** every recovery app listed — all of them phone home.

---

## 1. In-scope gaps — fit the product, low friction

These need no new sensitive permission and honor every hard invariant
(on-device, no network, encrypted, event-driven). Ordered by value/effort.

| # | Feature | Competitor doing it | Why it fits | Cost |
|---|---|---|---|---|
| 1 | **Per-rule time windows** (quiet hours / work hours: "VIP only 9–17") | BuzzKill, Tasker, Daywise | Pure rule-eval change; biggest single ask in filter apps | Add schedule fields to `rules`; check clock in `RuleEngine`. **DB version bump.** No perms. |
| 2 | **Per-conversation / per-sender rules** (not just per-app) | BuzzKill, Tasker | Vault already keys conversations; rule scope is the missing axis | `rules.conversation_key` nullable; engine filters. Arch: rule resolution gains a scope tier. |
| 3 | **Compound conditions (AND/OR)** ("keyword AND from VIP") | BuzzKill, MacroDroid | RuleEngine already evaluates each type; needs a combinator | Rule grouping model + engine change. Medium arch. No perms. |
| 4 | **Burst / frequency triggers** ("muted group suddenly active", "N msgs in M min from X") | Tasker, MacroDroid | Reuses `RepeatSenderTracker`; novel vs every competitor | New trigger type + sliding-window counter. No perms. |
| 5 | **Media recovery hardening** — proactively copy WhatsApp/IG media from shared storage before delete (WAMR's core trick) | **WAMR** | `READ_MEDIA_*` already held; `VaultMediaScreen` exists | `MediaStore` `ContentObserver` → copy to encrypted store. Event-driven, no new perm. |
| 6 | **Conversation/rule snooze** (temp mute a thread for 1h/until tomorrow) | Daywise, system | Inverse of existing alert path | Transient suppress state. No perms. |
| 7 | **Local encrypted backup/restore** (export vault+rules to a file, no cloud) | Notisave, Signal | Share-intent / SAF write needs **no** `INTERNET` | Serialize→encrypt→SAF. Flag: privacy review (vault holds others' content). |
| 8 | **Quick-settings tile + home widget** to toggle capture / enter decoy | many | Pure UI surface | `TileService` + Glance widget. No perms. |
| 9 | **Regex rule input exposed in UI** (engine supports it; verify editor surfaces it) | Tasker, BuzzKill | Catalog already compiles regex | UI-only if engine path exists; else small. |
| 10 | **OTP smart-copy** (auto-extract code to clipboard, not just auto-delete) | Messages, password mgrs | `OtpCleanupWorker` already parses OTPs | Reuse parser → `ClipboardManager`. No perms. |

---

## 2. In-scope but heavier — new app coverage / surfaces

Aligned with the privacy promise, but each is real engineering or a Play-review
conversation.

| Feature | Note | Cost / flag |
|---|---|---|
| **Telegram / Signal / Discord / Slack support** | Notification-source only; just new `AppPackage` + parsers. Telegram is the most-requested missing app. | New parsers + pattern-catalog entries + tests. **Accessibility scope widening is Play-sensitive** — keep notif-only unless needed. |
| **Status / Story saver** (WhatsApp/IG) | WAMR's #2 feature; uses media perms already held | New capture path off `MediaStore`; borderline-on-mission (saving vs filtering). |
| **On-device thread summary** (catch-up on a noisy group) | Must be **on-device ML only** — cloud LLM violates no-network | Heavy; ML Kit / small local model. Battery + APK-size flag. |
| **Tasker/automation plugin** (`PluginType` intents) | Power-user; stays on-device | New IPC surface; no network. |
| **Missed-call / call-event triggers** | Extends "VIP" to calls | Needs `READ_CALL_LOG` / `READ_PHONE_STATE` — **Play-restricted, flag.** |

---

## 3. Out of scope — conflict with hard invariants (document, don't build)

| Competitor feature | Why rejected |
|---|---|
| Cloud backup / multi-device sync (every recovery app) | Violates **no `INTERNET`** + fully-on-device invariants. Non-negotiable. |
| Auto-reply / send-from-notification (WAMR, Tasker) | PRD non-goal; would need send capability. |
| Forward notifications to PC/wearable over net | Needs networking dependency → breaks zero-exfiltration guarantee. |
| Ghost mode / read-receipt spoofing inside chat apps | Requires hooking app internals — only the inert Face 2 path touches this; scope fixed, don't widen. |
| Cloud AI summaries / smart replies | No network. (On-device variant is the in-scope item in §2.) |
| Location / geofence triggers | `ACCESS_FINE_LOCATION` is Play-sensitive and off-mission for a filter app. |

---

## Recommended next slice

Ship **§1 items 1–4 together** — time windows, per-sender scope, compound
conditions, burst triggers. They share one theme (a richer rule model), land in one
`rules` schema bump, need zero new permissions, and collectively move QuietPing from
"good notification filter" to "BuzzKill-class rule engine that's also private." Pair
with **§1 #5 (media recovery hardening)** to close the one place WAMR is genuinely
ahead.

**Permission/arch flags to watch:** every §1 item is permission-free; the only DB
migration is the rule-model bump (destructive migration is fine per project
convention — capture data is a rebuildable cache). New-permission asks all live in
§2/§3 and should not ship silently.
