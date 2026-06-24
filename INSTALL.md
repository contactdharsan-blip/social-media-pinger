# Installing QuietPing

QuietPing is a **fully on-device** Android app. It has **no `INTERNET` permission** — captured
message content never leaves the phone. It is **not on the Play Store**; you build the APK from
source and sideload it onto your device.

- **Build host:** Windows, macOS, or Linux
- **Target device:** Android 8.0 (API 26) or newer
- **Audience:** developers build the APK (Part 1); end users grant permissions (Part 3)

---

## Part 1 — Build the APK (developer host)

### Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | **17** | Required. Project compiles against Java 17 / `jvmTarget = 17`. |
| Android SDK | Platform **34**, Build-Tools 34.x | Installed via Android Studio or `cmdline-tools`. |
| Android Studio | Ladybug (2024.2) or newer | Optional but easiest. Bundles a JDK 17 + SDK manager. |
| Git | any | To clone the repo. |

Gradle itself is **not** a prerequisite — the repo ships the Gradle **8.10.2** wrapper
(`./gradlew`), which downloads the correct Gradle on first run.

Pinned build tooling (from `gradle/libs.versions.toml`): AGP 8.7.3, Kotlin 2.0.21,
KSP 2.0.21-1.0.25, Hilt 2.52.

### Clone

```bash
git clone https://github.com/contactdharsan-blip/social-media-pinger.git
cd social-media-pinger
```

### Point Gradle at the Android SDK

Create `local.properties` in the repo root (gitignored — never commit it):

```properties
# macOS / Linux
sdk.dir=/Users/<you>/Library/Android/sdk
# Windows
# sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Android Studio writes this for you when you open the project. If `ANDROID_HOME` /
`ANDROID_SDK_ROOT` is already exported, Gradle picks it up and the file is optional.

### Build

```bash
# Debug APK (unsigned, debuggable) — for quick install
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release APK (unminified; see note) — needs signing to install
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release-unsigned.apk
```

> Windows: use `gradlew.bat assembleDebug`.

> Release builds are **not** minified (`isMinifyEnabled = false`) and are produced **unsigned**.
> To install a release APK you must sign it with your own keystore (`apksigner`) — keystores
> (`*.jks` / `*.keystore`) are gitignored on purpose. For testing, prefer the debug APK.

### Run the test suite (optional)

```bash
./gradlew test          # JVM unit tests (parsers, rule engine, vault, AhoCorasick)
```

---

## Part 2 — Install on the device

### Quick start (recommended: Android Studio)

1. Enable **Developer options** on the phone: Settings → About phone → tap **Build number** 7×.
2. In Developer options, enable **USB debugging**.
3. Plug the phone into the build host, open the project in Android Studio, pick your device,
   press **Run** ▶. Studio builds, installs, and launches in one step.

### Or sideload the APK manually

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# reinstall over an existing copy, keeping data:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`adb` ships with Android SDK Platform-Tools. The device must have USB debugging on and
the host authorized (accept the on-device prompt).

---

## Part 3 — Grant permissions (end user, first launch)

QuietPing captures messages through Android's **special-access** services. These are **not**
the normal pop-up permissions — they must be toggled by hand in Settings. The in-app onboarding
walks you through each; manual paths below as backup.

| # | Permission | Why | Where |
|---|-----------|-----|-------|
| 1 | **Notification access** | Primary capture: reads chat-app notifications (WhatsApp, Instagram, Messenger, SMS). | Settings → Apps → Special app access → **Notification access** → enable QuietPing |
| 2 | **Notifications (POST)** | Show the re-notify heads-up alerts. | Normal runtime prompt on Android 13+, or Settings → Apps → QuietPing → Notifications |
| 3 | **SMS (READ_SMS)** | Full SMS history + deletion diffing. | Runtime prompt, or Settings → Apps → QuietPing → Permissions → SMS |
| 4 | **Do Not Disturb access** | Let VIP/keyword alerts bypass DND. | Settings → Apps → Special app access → **Do Not Disturb access** |
| 5 | **Accessibility** *(optional booster)* | Deeper capture for apps that truncate notifications. | Settings → Accessibility → QuietPing → enable |
| 6 | **Biometric** *(optional)* | Lock the Vault behind fingerprint/face. | Prompted when you enable app lock in-app |

> Notification access and Accessibility survive reboot — a `BOOT_COMPLETED` receiver rebinds
> capture automatically. No re-grant needed after restart.

---

## Troubleshooting

**`Unsupported class file major version` / Gradle JVM errors**
Wrong JDK. Confirm `java -version` reports 17. In Android Studio: Settings → Build → Build
Tools → Gradle → **Gradle JDK = 17**.

**`SDK location not found`**
Missing/wrong `local.properties` `sdk.dir`, or `ANDROID_HOME` unset. See Part 1.

**First `./gradlew` run hangs or fails downloading Gradle**
It is fetching Gradle 8.10.2. Check network/proxy. The wrapper validates the distribution URL;
do not edit `gradle-wrapper.properties`.

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE` on `adb install`**
A build signed with a different key is already installed. Uninstall first:
`adb uninstall com.quietping`.

**App installs but captures nothing**
Notification access (Part 3 #1) is off, or was reset by the OS. Re-enable it. Some OEMs
(Xiaomi/MIUI, Samsung, Huawei) aggressively kill background services — exempt QuietPing from
battery optimization in Settings → Apps → QuietPing → Battery → Unrestricted.

**Release APK won't install (`INSTALL_PARSE_FAILED_NO_CERTIFICATES`)**
The release APK is unsigned. Sign it with `apksigner`, or use the debug APK.

---

## Notes

- **App ID:** `com.quietping` · **versionName** 1.0 · **versionCode** 1
- Data is stored in a **SQLCipher-encrypted** Room database; the key is derived/held on-device.
- The app ships an **icon switcher** (Default / Mono / Stealth) — the launcher icon and name can
  be changed in-app for privacy; this does not affect installation.
