// Deliberately on an older, well-established toolchain (AGP 8.5 / Kotlin 2.0) rather than
// whatever is newest today: this is a personal, sideloaded app, not a Play Store submission,
// so a version that is known to work reliably matters more than being current.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
