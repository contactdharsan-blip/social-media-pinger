// Top-level build file. Plugin versions are declared here (apply false) and the
// concrete plugins are applied in the :app module. Keep all version coordinates
// in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
