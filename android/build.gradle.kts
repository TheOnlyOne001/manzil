// Pinned toolchain for the first runnable slice. Wrapper files are managed by the root tooling.
// AGP 8.11.1 / Gradle 8.13 / Kotlin 2.2.21 / JDK 17 / compileSdk 35.
plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
