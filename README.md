# QuietPing

**Smart conditional ping for Android. Silent by default; alerts only when it matters.**

A **free and open source**, fully **on-device** alert filter for WhatsApp, Instagram,
Messenger, and SMS. It has **no `INTERNET` permission** — captured message content never
leaves your phone. No servers, no analytics, no cloud sync. Developed in the open and
community-driven — read the source, build it yourself, and contributions are welcome.

---

## Download

[![Build & Release APK](https://github.com/contactdharsan-blip/social-media-pinger/actions/workflows/release.yml/badge.svg)](https://github.com/contactdharsan-blip/social-media-pinger/actions/workflows/release.yml)

Grab a ready-to-install APK straight from GitHub — no build tools needed:

**→ [Latest release](https://github.com/contactdharsan-blip/social-media-pinger/releases/latest)**

1. Open the release, expand **Assets**, download `QuietPing-<version>-debug.apk`.
2. Copy it to your Android device (8.0 / API 26 or newer) and tap it to install.
   Allow "install from unknown sources" if prompted.
3. On first launch, grant the special-access permissions — see **[INSTALL.md](INSTALL.md)** Part 3.

> The published APK is a **debug** build (signed with the standard Android debug key) so it
> installs with zero setup. To build a production-signed release yourself, see
> [INSTALL.md](INSTALL.md) Part 1.

Prefer to build from source? Full instructions are in **[INSTALL.md](INSTALL.md)**.

---

## What it does

See **[PRD.md](PRD.md)** for the project spec and **[CLAUDE.md](CLAUDE.md)** for the
architecture and engineering contract.

- **Event-driven capture** — notification listener / SMS observer, no polling.
- **On-device rule engine** — Aho-Corasick keyword + VIP matching decides what pings.
  Rules can **alert** or **suppress**, and fire only inside a quiet-hours time window.
- **Alert escalation** — standard, persistent (re-pings until you read it), or critical
  (full-screen + alarm channel, bypasses DND). Repeat senders auto-escalate.
- **Daily digest** — a once-a-day summary of everything that stayed silent.
- **Privacy quartet** — screenshot block (`FLAG_SECURE`), content-hidden notifications,
  a break-in log (failed-unlock attempts), and a decoy PIN that opens an empty vault.
- **SMS productivity** — OTP detection with auto-cleanup, finance/bill detection.
- **Encrypted at rest** — SQLCipher Room with a Keystore-wrapped key.
- **Vault** — archives matched, edited (full version history), and deleted messages
  locally; lockable behind biometrics. Includes a captured-media gallery; alert taps
  deep-link straight to the thread.

> **Deep capture (Face 2, rooted only).** An optional LSPosed module recovers
> "delete-for-everyone" messages from inside the chat apps themselves. It ships **inert**
> — it does nothing unless you are rooted with LSPosed *and* explicitly enable and scope
> it. See [FACE2_XPOSED_RD.md](FACE2_XPOSED_RD.md).

---

## Open source

QuietPing is developed in the open. The full source lives in this repository — nothing is
hidden behind a proprietary blob, and the no-network privacy promise is auditable by anyone:
clone the repo and read it.

- **Build it yourself** — see **[INSTALL.md](INSTALL.md)**.
- **Contributions welcome** — issues and pull requests are the way the project moves
  forward; it's community-driven, not a closed product.
- **Verify the privacy claims** — there is no `INTERNET` permission and no networking
  dependency anywhere in the tree; grep for it and confirm.
- **License** — released under the [MIT License](LICENSE). Use it, fork it, ship it.
