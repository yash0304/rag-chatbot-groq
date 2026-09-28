plugins {
    id("com.android.application") version "8.5.2" apply false
    // Kotlin 2.2 because LiteRT-LM (the on-phone Gemma runtime) is built with a newer Kotlin
    // than 2.0 can read. KSP 2.3 is the matching annotation processor; the same pairing is
    // what Google's AI Edge Gallery builds with.
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
}
