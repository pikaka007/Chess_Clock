// Top-level build file. Versions are pinned to what the local Gradle cache holds
// (see docs/environment.md §5), so the build does not depend on network access.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
