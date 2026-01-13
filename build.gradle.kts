// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Google services plugin declared; will be applied in app module directly by id.
    id("com.google.gms.google-services") version "4.4.4" apply false
}
