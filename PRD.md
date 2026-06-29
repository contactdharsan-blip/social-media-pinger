# PRD — QuietPing

**Smart conditional ping for Android. Silent by default; alerts only when it matters.**

| | |
|---|---|
| **Status** | Draft v1 |
| **Platform** | Android (minSdk 26 / Android 8.0, target latest) |
| **Apps covered** | WhatsApp, Instagram, Facebook/Messenger, Messages (SMS/MMS) |
| **Network** | None — fully on-device (no `INTERNET` permission) |
| **Distribution** | Free and open source — developed in the open; build from source or sideload |
| **Design language** | Dark Liquid-Glass (see `/Users/Dharsan/Downloads/identification med/DESIGN.md`) |

---

## 1. Overview & problem

Modern chat apps are all-or-nothing: either every message buzzes, or you mute a
conversation and miss the one message that actually mattered. People mute noisy
groups and then miss being @-mentioned, a poll they needed to vote in, or a
message from someone important.

**QuietPing** is an on-device alert filter. It watches the notifications (and, for
SMS, the message store) of your chat apps and **re-notifies you only when a
condition you defined fires** — your name mentioned, a poll created, a reply, a VIP
sender, a keyword, or a group event. Everything else stays silent.

It additionally keeps a private, encrypted **Message Vault** so you can read
**edited** (full version history) and **deleted** ("recovered") messages, and lets
you **customize** the app icon and UI theme.

> One line: *an on-device alert filter that re-notifies you only when your
> conditions fire, and quietly archives what others edit or delete.*

QuietPing is **free and open source** and community-driven. Its privacy promise is not a
marketing claim but an auditable property of the source: the no-network guarantee can be
verified by anyone reading this repository.

---

## 2. Goals / non-goals

**Goals**
- Precise, low-noise conditional alerts across the 4 apps.
- Battery-light — event-driven, no polling.
- Fully on-device and private — no servers, no network.
- Recover edited/deleted message text where Android permits.
- User-customizable icon + UI.

**Non-goals (v1)**
- Sending or replying to messages.
- Reading full chat history from inside chat apps (impossible without root — see §6A).
- Cloud sync / backup / multi-device.
- Scraping, automation, or bot behavior.
- Supporting every messaging app at launch.

---

## 3. Personas & top user stories

- **The muted-group survivor** — "I muted a 200-person group but must know the
  instant someone @-mentions me."
- **The poll-watcher** — "When a poll drops in any of my groups, ping me so I vote
  before it closes."
- **The VIP filter** — "Only ping me for messages from my partner, my boss, and my
  kid's school — silence the rest."
- **The keyword sentinel** — "Alert me whenever anyone mentions my project name or
  my username."
- **The receipt-keeper** — "I want to see what someone wrote before they deleted or
  edited it."

---

## 4. Architecture

QuietPing is a single-Activity Jetpack Compose app built on **Clean Architecture +
MVVM** with unidirectional data flow.

```
Capture (Android services)            Domain                      Data
─────────────────────────            ──────                      ────
NotificationListenerService ─┐      Per-app Parser strategy       Room (SQLCipher)
SMS/MMS ContentObserver     ─┼──►   RuleEngine (Aho-Corasick)     DataStore (settings)
[opt-in] AccessibilitySvc   ─┘      VaultManager (dedupe/version) Repositories (Flow)
                                    AlertDispatcher
                                          │
                              Presentation: Compose screens + ViewModels (StateFlow), Hilt DI
```

**Capture layer (Android services).** Three sources all normalize into one sealed
`RawEvent` and hand off immediately to a coroutine ingestion `Channel` on
`Dispatchers.IO` — the binder/callback thread is never blocked.
- `PingNotificationListenerService` (`NotificationListenerService`) — reads
  `StatusBarNotification` extras: `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, and
  `MessagingStyle` message lists. Primary source for all chat apps.
- `SmsObserver` (`ContentObserver` on `content://sms` / `content://mms`) — reads
  new/changed rows for the Messages app (full local store, see §6A).
- `PingAccessibilityService` (opt-in) — `TYPE_NOTIFICATION_STATE_CHANGED` +
  window-content events, **filtered to the 4 target packages only**. Extends
  coverage to silenced/never-notified content.

**Domain layer.**
- **Parsers** — `MessageParser` interface with `WhatsAppParser`, `InstagramParser`,
  `MessengerParser`, `SmsParser` impls that normalize per-app quirks into a domain
  `Message`.
- **RuleEngine** — evaluates a `Message` against enabled conditions using a
  precompiled keyword automaton (Aho-Corasick) + compiled regex. O(n) over text.
- **VaultManager** — dedupes via stable key hash, appends versions, flags deletions.
- **AlertDispatcher** — fires the matched alert (channel, sound, vibration, DND).

**Data layer.** Encrypted Room (SQLCipher) + DataStore, exposed through repositories
returning `Flow`. Schema in §6C.

**Flow:** source notification / SMS row → capture service → `RawEvent` → parser →
domain `Message` → `RuleEngine` (match?) → `AlertDispatcher` (ping) **and**
`VaultManager` (archive) → Room → reactive Compose UI.

---

## 5. Per-app detection matrix

| Trigger | WhatsApp | Instagram | Messenger/FB | Messages (SMS/MMS) |
|---|---|---|---|---|
| Name mention | Notif text (group msg body) | Notif "mentioned you" | Notif text | Notif + provider |
| Poll created | Notif "📊 Poll:" sentinel | Story/notes poll notif | Notif text | n/a |
| Custom keyword | Notif text | Notif text | Notif text | **Provider (full)** + notif |
| Reply / @mention | Notif quoted/`@name` | Notif "replied/mentioned" | Notif text | Provider/notif |
| VIP contact | Notif sender/title | Notif sender | Notif sender | Provider sender |
| Group event | Notif (added/admin/call) | Limited | Notif | n/a |

**Fragility:** detection depends on the *text format* of each app's notifications,
which varies by app version and **device language/locale**. Mitigation: a
maintainable, versioned **pattern catalog** (sentinels + regex per app/locale),
covered by unit tests and refreshed on an app-update cadence. Where a pattern can't
be matched reliably, the condition degrades gracefully (no false ping) rather than
guessing.

---

## 6. Technical detail

### 6A. How app data is accessed locally (the hard truth)

There are exactly three sanctioned on-device channels; **two require no
AccessibilityService**:

| App | Accessibility-free channel | History depth | Deleted recoverable | Edited history | Hard limits |
|---|---|---|---|---|---|
| WhatsApp | NotificationListener | live only | Yes, if it was notified (revoked-msg sentinel) | Partial | no media/voice body; truncated previews |
| Instagram | NotificationListener | live only | Limited | Limited | DM previews short/absent |
| Messenger/FB | NotificationListener | live only | Limited | Limited | similar to IG |
| Messages (SMS/MMS) | **ContentProvider** `content://sms`,`content://mms` (`READ_SMS`) + NotificationListener | **full local store** | **Yes — diff provider vs cache** | derivable | RCS in Google Messages mostly **not** exposed |

**Why chat apps can't go deeper:** WhatsApp/IG/Messenger store messages in their
private sandbox (`/data/data/<pkg>/…`). Android app sandboxing blocks reading them
without **root**, and WhatsApp's on-disk backup is E2E-encrypted with an
account-bound key. Therefore, for these three apps the **notification stream is the
ceiling** — unless the user opts into Accessibility (wider live coverage) or the
device is rooted (out of scope). The PRD intentionally does **not** promise full
history for chat apps.

**SMS is the exception.** `READ_SMS` + `ContentResolver` reads the entire local
SMS/MMS database; a `ContentObserver`-driven diff against our cache detects
deletions reliably. **Caveat:** `READ_SMS` is a Google Play **restricted
permission** → requires a Permissions Declaration Form with justification, or
shipping as a user-selected default-SMS handler. Carries real rejection risk (§11).

### 6B. Trigger conditions spec

- **Name mention** — user's configured display name(s)/handle(s) appear in message
  body, or the app's own "mentioned you" notification fires.
- **Poll created** — app-specific poll sentinel detected in notification text.
- **Custom keyword** — any user-defined term/phrase matches (case-insensitive,
  word-boundary aware) via the Aho-Corasick automaton.
- **Reply / @mention** — message quotes the user or contains `@handle`.
- **VIP contact** — sender matches a starred contact for that app, regardless of
  content.
- **Group event** — added to group / admin change / call / voice note (per-app
  availability).

### 6C. How info is stored (Room schema, encrypted)

SQLCipher-encrypted Room; passphrase wrapped by the Android **Keystore**. Core
tables:

- `conversations(id, app_package, conversation_key, display_name, is_group)`
- `messages(id, conversation_id, sender, posted_at, captured_at, source[NOTIFICATION|SMS|ACCESSIBILITY], status[ACTIVE|EDITED|DELETED], current_version_id)`
- `message_versions(id, message_id, version_no, body, captured_at)` — **append-only ⇒ free edit history**
- `rules(id, app_package, type, pattern, enabled)`
- `vip_contacts(id, handle, app_package)`
- `match_log(id, message_id, rule_id, fired_at, channel)`

**Edit detection:** same message key + changed body within a time window → new
`message_versions` row, `status=EDITED`. **Deletion detection:** chat apps →
revoked-message sentinel / notification-removal heuristic; SMS → row present in our
cache but gone from the provider. All DAOs return `Flow` for reactive UI. A
periodic **WorkManager** job purges rows past the user's retention window.

### 6D. Message Vault (edited & deleted messages)

- **Capture:** every incoming chat message (text, sender, conversation, timestamp)
  is written to the encrypted store the moment it arrives — no extra permission
  beyond notification access.
- **Deleted → Recovered:** when the source later shows "This message was deleted"
  or removes the notification, the cached original is surfaced as *Recovered*.
- **Edited → version history:** successive versions of the same message id are kept
  in order (v1 → v2 → …) with a diff and an "edited" badge.
- **Where shown:** the **Message Vault** screen — conversation list → per-conversation
  thread. Deleted items tagged *Recovered* (strikethrough styling); edited items
  expand to full version history. Filters: per-app, deleted-only, edited-only,
  search.
- **Constraints:** notification path recovers text/captions only (no media/voice);
  coverage requires notifications to be enabled for the source chat.

### 6E. Customization

- **App icon switcher** — multiple `<activity-alias>` entries (Default, Mono, Color
  variants, and a **disguised** icon that doubles as a privacy feature for the
  Vault), toggled via `PackageManager.setComponentEnabledSetting`. UX note: icon
  switch triggers a brief launcher refresh; communicate this in-app.
- **UI customization** — driven by the DESIGN.md themeable tokens: accent picker
  (emerald→teal default + presets), glass intensity (blur/opacity), motion toggle
  (honors reduced-motion), dark default / optional light. Persisted in DataStore,
  applied through Compose `MaterialTheme` + a custom `LiquidGlass` theme provider.
- **Where shown:** the **Appearance** settings screen (icon grid + live-preview
  theme controls).

### 6F. Efficiency & lifecycle

- Fully **event-driven** — listener callbacks + SMS `ContentObserver`; **no polling
  loops** anywhere.
- The bound `NotificationListenerService` is Doze-exempt; rebind on
  `BOOT_COMPLETED` and on listener-disconnect.
- DB writes are batched; Accessibility (if enabled) is filtered to the 4 packages
  and a minimal set of event types.
- **No `INTERNET` permission** → the OS itself guarantees zero exfiltration.
- **Battery target:** < ~1%/day attributable drain under typical messaging volume.

---

## 7. Permissions & onboarding

Required / used: `BIND_NOTIFICATION_LISTENER_SERVICE`, `READ_SMS`,
`POST_NOTIFICATIONS`, `ACCESS_NOTIFICATION_POLICY` (DND override),
`RECEIVE_BOOT_COMPLETED`. Optional: `BIND_ACCESSIBILITY_SERVICE`. **Deliberately
omitted:** `INTERNET`.

Stepped onboarding requests each permission with a plain-language reason and a
deep-link to the system setting; every step is skippable (the app degrades to the
features that permission unlocks). Prominent disclosure copy is shown before
Notification, SMS, and Accessibility grants — required for Play review.

---

## 8. Alert behavior & sound presets

**Alert model:** one `NotificationChannel` per condition type → independent sound,
vibration, importance, and DND-bypass settings. Matches fire `IMPORTANCE_HIGH`
heads-up notifications; VIP/name conditions may bypass DND via
`ACCESS_NOTIFICATION_POLICY`. Low-priority conditions can be batched into a digest
(WorkManager).

**Sound presets** (bundled short `<1.5s` tones in `res/raw/*.ogg`, loudness-
normalized; user can reassign any preset per condition):

| Preset | Character | Default use |
|---|---|---|
| **Droplet** | soft liquid plip (signature) | global default / keyword |
| **Glass Tap** | crisp high tick | reply / @mention |
| **Sonar** | two-tone ping w/ tail | VIP contact |
| **Chime** | bright ascending 3-note | poll created |
| **Pulse** | low double-thump | name mentioned (urgent) |
| **Whisper** | very soft, low volume | digest / low-priority batch |
| **Silent+** | no sound, vibrate-only | mute-but-vibrate conditions |

Each preset pairs with a default vibration pattern; both overridable. User changes
made in system channel settings are respected (never silently overridden).

---

## 9. UI / UX

### 9.1 Screen map (single-Activity Compose nav graph)

```
AppLock (biometric gate, if enabled)
   │
   ▼
Onboarding ──► Permission steps: Notification access → SMS (opt) → DND → Accessibility (opt)
   │
   ▼
Home (bottom nav: Home · Vault · Rules · Settings)
   ├─ Home/Dashboard ── per-app toggle cards (WA/IG/Messenger/SMS) + live match feed
   │                     └─► Match detail ─► jump to source / Vault thread
   ├─ Message Vault ──── conversation list ─► Thread view (EDITED history / DELETED "Recovered")
   │                     filters: app · deleted-only · edited-only · search
   ├─ Rules ──────────── per-app rule list ─► Rule editor
   │                     └─► Keyword editor · VIP picker · toggles (name/poll/reply/group)
   └─ Settings
        ├─ Alert settings ─► per-condition: channel, sound preset (§8), vibration, DND override
        ├─ Appearance ────► app-icon grid + theme controls, live preview
        ├─ Privacy & lock ► biometric lock, retention window, purge now (export disabled — no INTERNET)
        └─ About / permissions status
```

Match history lives inside Home's feed (filterable), not a separate tab.

### 9.2 Theme mapping (DESIGN.md → Jetpack Compose)

| DESIGN.md token | Compose mapping |
|---|---|
| `--color-bg-primary #030712` | `Color(0xFF030712)` app background |
| Accent emerald/teal `#34d399`/`#14b8a6` | `MaterialTheme.colorScheme.primary`/`secondary` |
| Glass surfaces (`--glass-*`) | translucent `Surface` + `Modifier.blur` / `RenderEffect` (API 31+), opaque fallback below |
| Spring motion `cubic-bezier(0.34,1.56,0.64,1)` | Compose `spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)` |
| Fonts Bricolage Grotesque / General Sans | bundled `FontFamily` (display / body) |
| Icons (stroke, never emoji) | Material/Lucide-style vector icons via typed lookup |

Honor `prefers-reduced-motion` (system "Remove animations") and degrade glass
blur/ambient effects on low-end devices.

---

## 10. Tech stack

Kotlin · Jetpack Compose + Material 3 · Room (SQLCipher) · DataStore · Hilt ·
WorkManager · Coroutines/Flow. minSdk 26, target latest stable.

---

## 11. Privacy & risks

**Privacy.** 100% on-device, no network (no `INTERNET` permission → provable). The
Vault stores *other people's* message content, so: SQLCipher encryption at rest,
Keystore-wrapped key, configurable retention + auto-purge, optional biometric app
lock, optional disguised icon, and a clear in-app disclosure that captured content
belongs to third parties.

**Risks & mitigations.**
- *Play policy rejection* — NotificationListener, Accessibility, and especially
  `READ_SMS` are scrutinized. Mitigate with genuine core-function justification,
  prominent disclosure, Permissions Declaration Form, or default-SMS-handler route.
- *Parsing fragility* — app/locale notification-format changes break detection.
  Mitigate with a versioned pattern catalog + tests + update cadence; fail safe (no
  false ping).
- *Deletion ambiguity (chat apps)* — notification removal ≠ deletion. Rely on
  explicit revoked-message sentinels + heuristics; mark uncertain cases distinctly.
- *OEM quirks* — aggressive battery managers may kill the listener; guide users to
  whitelist; rebind on boot/disconnect.

---

## 12. Success metrics

Alert precision/recall · false-ping rate · battery drain %/day · time-to-alert
latency · Vault recovery rate (deleted/edited caught) · D30 retention.

---

## 13. Phased roadmap

- **V1** — NotificationListener; WhatsApp + Messenger; name/keyword/VIP triggers;
  Message Vault (notification-cache deleted/edited); app-icon switcher + theme
  customization; sound presets.
- **V2** — Instagram + Messages (SMS provider); poll/reply/group triggers; digest.
- **V3** — opt-in Accessibility booster (wider Vault coverage); advanced rules.

---

## 14. Open questions / out of scope

- Ship `READ_SMS` (declaration form) vs become default-SMS handler vs drop SMS-deep
  features for v1?
- Default retention window length?
- Disguised-icon set — how many variants, and naming?
- Out of scope: rooted-device deep DB access, message sending, cloud/multi-device.
```
