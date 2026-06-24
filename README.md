# QuietPing

**Smart conditional ping for Android. Silent by default; alerts only when it matters.**

Fully **on-device** alert filter for WhatsApp, Instagram, Messenger, and SMS. It has **no
`INTERNET` permission** — captured message content never leaves your phone. No servers, no
analytics, no cloud sync.

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

See **[PRD.md](PRD.md)** for the product spec and **[CLAUDE.md](CLAUDE.md)** for the
architecture and engineering contract.

- **Event-driven capture** — notification listener / SMS observer, no polling.
- **On-device rule engine** — Aho-Corasick keyword + VIP matching decides what pings.
- **Encrypted at rest** — SQLCipher Room with a Keystore-wrapped key.
- **Vault** — archives matched and deleted messages locally; lockable behind biometrics.
