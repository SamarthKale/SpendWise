# DECISIONS

Notes on choices where CLAUDE.md was ambiguous or tooling forced a call.

1. **Map = OpenStreetMap (osmdroid), not Google Maps.** Per the override block. No Maps API key,
   Secrets Gradle Plugin, or maps-compose. The lab's "Google Maps" outcome is satisfied with OSM as a free alternative.
2. **AGP 9.4.1 + Gradle 9.7.1 + Kotlin 2.4.20.** AGP 9 has built-in Kotlin, so the `kotlin-android` plugin is
   not applied; only the Kotlin Compose compiler plugin is. Java sources still work under `src/main/java`.
3. **compileSdk/targetSdk 37.** Current stable AndroidX (core-ktx 1.19) requires compileSdk 37. Installed via
   `sdkmanager "platforms;android-37.0"`. minSdk stays 26.
4. **Java 17 source/target level** for the Java backend package (JDK 21 runs the build).
