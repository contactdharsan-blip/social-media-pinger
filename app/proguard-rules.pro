# QuietPing ProGuard / R8 rules.
# Minification is disabled for release in v1; keep this file as the project hook
# so rules can be added without touching build.gradle.kts.

# Hilt / Dagger generated code is handled by the Hilt Gradle plugin's consumer rules.
# Room generated DAOs are handled by the Room consumer rules.
# SQLCipher native bindings — keep the JNI entry points.
-keep class net.sqlcipher.** { *; }
-keep interface net.sqlcipher.** { *; }
