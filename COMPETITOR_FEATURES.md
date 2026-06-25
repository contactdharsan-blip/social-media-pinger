# Competitor Feature Research — QuietPing

On-device research (2026-06-24) across 4 adjacent Android categories. Every gap
below is filtered against QuietPing's hard invariant: **no `INTERNET` permission,
fully on-device.** Cloud/sync features are listed only to mark them OUT (do not build).

Legend: **COVERED** = QuietPing already has it · **GAP** = missing, on-device-safe ·
**PARTIAL** = partly there · **VIOLATES** = breaks no-network, do not build.

---

## Category A — Deleted/edited-message recovery (QuietPing's core)

| App | Installs | Mechanism | Notable extras |
|---|---|---|---|
| WAMR | 100M+ | NotificationListener snapshot | status/story saver, media re-download |
| TDA Unseen | ~25M | NotificationListener | read-without-blue-tick, status saver |
| WhatsRemoved+ | 10M+ | NotificationListener | media recovery, multi-app claim |
| Notisave | 10M+ | NotificationListener | read-without-receipt, status save, per-app ignore |
| WhatsDelete (Muster) | ~1M | NotificationListener | WA cleaner, duplicate-media finder, selective backup |

**Findings:**
- "Multi-app" claims are mostly marketing — most ship WhatsApp-only or WhatsApp+SMS.
  QuietPing's tested WA/IG/Messenger/SMS capture is a **real** differentiator.
- **Edit / version history** is marketed by none of them — QuietPing-unique.
- Category is **NOT zero-network** — several require Internet for ads/error reporting
  even while claiming "local storage". QuietPing's no-INTERNET guarantee is a near-unique wedge.

**Gaps worth copying (on-device):**
- **Status/story saver** — read `.../WhatsApp/Media/.Statuses` (GAP; fits MediaVault)
- **Read-without-read-receipt** framing — QuietPing already reads from the vault (COVERED; market it)
- **Duplicate-media finder / cleaner** (GAP; low priority)

**VIOLATES:** cloud backup, status re-download from network, online/last-seen polling.

---

## Category B — Notification filter / rule engines (closest competitors)

| App | Note | Mechanism |
|---|---|---|
| **BuzzKill** (★ closest) | paid, **no network, no ads/trackers** — same philosophy | NotificationListener rule engine |
| Bouncer / FilterBox | temporary-grant + rule filtering | NotificationListener |
| Daywise | scheduled digest + VIP-interrupt allow-list | NotificationListener |
| Tasker / Automate | IFTTT-style triggers+actions | accessibility/listener |

**BuzzKill = the feature roadmap for QuietPing's alert layer.** Rule sentence model
("When [app] notif [contains X] then [action]"). Actions: **Remind me** (re-ping until
opened), **Alarm** (full-screen, fires in silent/DND), **Snooze**, **Cooldown** (alert
first of a burst, mute rest N min), **Batch** (digest), Reply, Undo/history.

| Feature | QuietPing status |
|---|---|
| Per-app / keyword / contact rule targeting | COVERED |
| Per-condition custom sounds | COVERED |
| **Re-ping-until-read loop** | **GAP** (dispatcher fires once) → Bundle 1 |
| **Read-detection to stop reminders** (`onNotificationRemoved`) | **GAP** (signal captured, unused) |
| **Escalation / full-screen alarm** | **GAP** → Bundle 1 |
| Snooze / "remind me in X" | **GAP** |
| **Cooldown / anti-spam** | **GAP** |
| **Scheduled digest / batch** | **GAP** → Bundle 2 |
| **Quiet hours / time-windowed rules** | **GAP** → Bundle 2 |

---

## Category C — VIP / reminder / escalation

| App / OS feature | Pattern |
|---|---|
| Missed Notifications Reminder | beep/vibrate loop until unread leaves active set |
| Reminder FLEX | interval × count (up to 90×), "re-notify in 15 min" |
| SOS Ring (OSS) | **ALARM-stream ring-through** bypasses all DND; save/restore ringer state |
| iOS Time Sensitive / Critical | interruption levels; repeated-caller break-through |
| Android Modes / Cooldown / Repeat callers | starred-contact + repeat-caller DND exceptions |

| Feature | QuietPing status |
|---|---|
| VIP break-through | COVERED |
| DND override per rule | PARTIAL (channel `setBypassDnd`; no ALARM-stream hard bypass) → Bundle 1 |
| **Repeated-sender break-through** ("same sender 2× in N min → escalate") | **GAP** → Bundle 1 |
| Above-lock / full-screen takeover | **GAP** → Bundle 1 |

**Key technique:** play through the **ALARM audio stream** + `setFullScreenIntent()` +
channel `setBypassDnd(true)`; on Android 14+ `USE_FULL_SCREEN_INTENT` is restricted
to call/alarm apps (user-grantable). Save/restore ringer+DND state around a bypass.

---

## Category D — SMS manager + privacy/vault

### SMS productivity (Microsoft SMS Organizer, Google Messages, Pulse, Textra)
| Feature | Network | QuietPing status |
|---|---|---|
| On-device categorization (Personal/Transactional/Promo) | OK | **GAP** → Bundle 4 |
| OTP detect + highlight + one-tap copy | OK | **GAP** → Bundle 4 |
| **OTP auto-delete after N hours** | OK | PARTIAL (has retention; not OTP-targeted) → Bundle 4 |
| **Finance/bill-due reminder parsed from SMS** | OK (regex) | **GAP** (high value) → Bundle 4 |
| **Block by keyword/phrase** | OK | **GAP** (extends rule engine) → Bundle 4 |
| Search across messages | OK | COVERED |
| Per-conversation hide-content notification | OK | GAP → Bundle 3 |
| **Local file export (XML/JSON to device/SAF)** | OK | **GAP** (only no-network "backup") → Bundle 4 |
| Cloud backup / Drive sync / cross-device | NET | **VIOLATES — do not build** |

### Privacy / vault (Calculator-Vault, AppLock, Keepsafe, Samsung Secure Folder)
| Feature | QuietPing status |
|---|---|
| Disguised app icon | COVERED (activity-alias) |
| Biometric lock + encrypted vault + retention/purge | COVERED |
| **Decoy / fake-PIN → fake vault** | **GAP** → Bundle 3 |
| **Silent break-in / unlock-attempt log** | **GAP** (cheaper + lower Play-risk than intruder selfie) → Bundle 3 |
| **Screenshot block (FLAG_SECURE)** | **GAP** (trivial, expected) → Bundle 3 |
| **Content-hidden notifications** (Secure-Folder style) | **GAP** → Bundle 3 |
| PIN/pattern fallback (not just biometric) | PARTIAL → Bundle 3 |
| Auto-lock timing / lock-on-screen-off | GAP |
| Random/incognito keypad (anti-shoulder-surf) | GAP (low effort) |
| **Intruder selfie** | SKIP — camera permission = Play review risk |
| Cloud / private-cloud backup | **VIOLATES — do not build** |

---

## Selected build scope (user-approved, all 4 bundles)

- **Bundle 1 — Alert escalation:** re-ping-until-read (stop on `onNotificationRemoved`),
  full-screen critical alert + ALARM-stream DND bypass, repeated-sender break-through.
- **Bundle 2 — Time + digest:** per-rule quiet hours / time-window, scheduled batched digest.
- **Bundle 3 — Privacy quartet:** FLAG_SECURE, break-in log, decoy fake-PIN, content-hidden notifications.
- **Bundle 4 — SMS productivity:** OTP detect+auto-delete, finance/bill reminders, keyword/phrase block, ~~local export~~.

### Build outcome (2026-06-25) — all 4 bundles shipped
Verified: `testDebugUnitTest` (97 tests, 0 fail) + `assembleDebug` green; merged manifest NO INTERNET; no okhttp/ktor/retrofit. New domain logic unit-tested (`RepeatSenderTracker`, `Rule.activeAt`, `PinHasher`, OTP/finance detection).

**`local export` deliberately NOT built.** Although the research flagged local-file export as the only no-network-safe "backup", QuietPing has an established **no-export-by-design** privacy stance: the Privacy screen's `ExportDisabledNote` tells users "nothing can ever leave this device… intentionally no export or backup", and the vault holds *other people's* messages. A SAF export moves that content out of the encrypted sandbox to user-chosen (shareable) storage, weakening the promise. Skipping it keeps the differentiator intact.

Implementation plan + status tracked in `tasks/todo.md`.
