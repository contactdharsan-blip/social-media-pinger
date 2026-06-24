# BUILD_STATUS — QuietPing

**Verdict: COMPILES + UNIT TESTS PASS.**
`:app:compileDebugKotlin` BUILD SUCCESSFUL and `:app:testDebugUnitTest` BUILD SUCCESSFUL (59 JVM unit tests, 0 failures). Verified locally, offline, against the on-disk Gradle cache. No network was required.

---

## Environment

| Item | Value |
|---|---|
| **JDK used** | OpenJDK **21.0.10** (Android Studio JBR) at `/Applications/Android Studio.app/Contents/jbr/Contents/Home` — exported as `JAVA_HOME`. (PATH `java` is 11.0.16 — too old, not used.) |
| **Android SDK** | `/Users/Dharsan/Library/Android/sdk` |
| **Installed platforms** | `android-34`, `android-35` — **android-34 present**, so `compileSdk`/`targetSdk = 34` need no change. |
| **Installed build-tools** | `34.0.0`, `35.0.0`, `37.0.0` |
| **Gradle** | wrapper-pinned **8.10.2** |
| **local.properties** | was **MISSING** → created with `sdk.dir=/Users/Dharsan/Library/Android/sdk` |
| **`timeout`/`gtimeout`** | not installed on this machine; per-invocation caps were applied at the tool level instead of the shell `timeout` wrapper. |

---

## Commands run (from project root, in order)

1. `./gradlew :app:compileDebugKotlin --offline --console=plain` → **FAILED** (1st attempt) — `processDebugResources`: Android resource linking failed (see error below). Not a dependency-download failure, so `--offline` was kept.
2. *(applied fix #1 to `app/src/main/res/values/themes.xml`)*
3. `./gradlew :app:compileDebugKotlin --offline --console=plain` → **BUILD SUCCESSFUL in 18s** (resources linked, `kspDebugKotlin` Hilt+Room codegen ran, Kotlin compiled).
4. `./gradlew :app:testDebugUnitTest --offline --console=plain` → **BUILD SUCCESSFUL in 16s**.

All invocations ran fully **offline** against `~/.gradle/caches` — no `--offline` retry without offline was needed; network was never contacted, so network-blocking was never a factor.

---

## PASS / FAIL

| Step | Command | Result |
|---|---|---|
| Locate JDK 17+ | — | PASS (JDK 21 JBR) |
| local.properties / SDK | — | PASS (created; android-34 present) |
| Kotlin compile | `:app:compileDebugKotlin --offline` | **PASS** (after fix #1) |
| JVM unit tests | `:app:testDebugUnitTest --offline` | **PASS** |

### Unit test results — 59 tests, 0 failures, 0 errors, 0 skipped

| Suite | tests | failures |
|---|---|---|
| `domain.parser.ParsersTest` | 15 | 0 |
| `domain.parser.PatternCatalogTest` | 6 | 0 |
| `domain.rules.AhoCorasickTest` | 9 | 0 |
| `domain.rules.RuleEngineImplTest` | 14 | 0 |
| `domain.vault.StableKeyTest` | 7 | 0 |
| `domain.vault.VaultManagerImplTest` | 8 | 0 |
| **TOTAL** | **59** | **0** |

---

## Fixes applied

### Fix #1 — `app/src/main/res/values/themes.xml` (resource link failure)

**Symptom (1st compile attempt, tail of error):**
```
> Task :app:processDebugResources FAILED
   > Android resource linking failed
     error: resource style/Theme.Material3.DayNight.NoActionBar (aka com.quietping:style/Theme.Material3.DayNight.NoActionBar) not found.
     .../values/values.xml:1889: error: style attribute 'attr/colorOnPrimary (aka com.quietping:attr/colorOnPrimary)' not found.
     .../values/values.xml:1890: error: style attribute 'attr/colorSecondary ...' not found.
     .../values/values.xml:1891: error: style attribute 'attr/colorOnSecondary ...' not found.
     error: failed linking references.
```

**Root cause:** `Theme.QuietPing` used `parent="Theme.Material3.DayNight.NoActionBar"` and the View-based Material Components attrs `colorOnPrimary` / `colorSecondary` / `colorOnSecondary`. Those come from the **`com.google.android.material:material`** library, which this app deliberately does **not** depend on — it is a Compose-only app (`androidx.compose.material3`), and there is no View-based Material Components on the classpath. So aapt2 could not resolve the parent or those attrs.

**Fix:** retargeted the XML theme to the framework base `@android:style/Theme.Material.NoActionBar` (available since API 21; minSdk here is 26) and dropped the Material-Components-only attrs, keeping only framework attrs (`android:colorPrimary`, `android:colorAccent`) plus the existing window-background / edge-to-edge system-bar items. This is correct and non-cosmetic: the XML theme only governs the Activity window (near-black background to prevent a white pre-Compose flash + transparent system bars). All real theming is done in Compose Material3 (`com.quietping.ui.theme`), which is unaffected. No new dependency, no networking, no build-file edits.

> Note on file ownership: this change is to a **resource file**, not to `build.gradle.kts` / `settings.gradle.kts` / `AndroidManifest.xml` / any gradle file, so it is within the latitude the verifier role is granted ("you may edit any source file") and does not violate the shared-file rule.

---

## Non-fatal warnings (left as-is — not errors)

- `compileDebugKotlin`: `AppToggleCard.kt:54` — `Icons.Filled.Message` is deprecated in favor of `Icons.AutoMirrored.Filled.Message`. Compiles fine; cosmetic deprecation only.

---

## Files created / modified by the verifier

- **created** `local.properties` (sdk.dir)
- **created** `BUILD_STATUS.md` (this file)
- **modified** `app/src/main/res/values/themes.xml` (Fix #1)
