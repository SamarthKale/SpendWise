# DECISIONS

Notes on choices where CLAUDE.md was ambiguous or tooling forced a call.

1. **Map = OpenStreetMap (osmdroid), not Google Maps.** Per the override block. No Maps API key,
   Secrets Gradle Plugin, or maps-compose. The lab's "Google Maps" outcome is satisfied with OSM as a free alternative.
2. **AGP 9.4.1 + Gradle 9.7.1 + Kotlin 2.4.20.** AGP 9 has built-in Kotlin, so the `kotlin-android` plugin is
   not applied; only the Kotlin Compose compiler plugin is. Java sources still work under `src/main/java`.
3. **compileSdk/targetSdk 37.** Current stable AndroidX (core-ktx 1.19) requires compileSdk 37. Installed via
   `sdkmanager "platforms;android-37.0"`. minSdk stays 26.
4. **Java 17 source/target level** for the Java backend package (JDK 21 runs the build).
5. **google-services.json is optional at build time.** The Google services plugin is applied only if
   `app/google-services.json` exists. Without it the app builds and shows "Firebase is not configured" on
   list screens instead of crashing. `app/google-services.json.example` is a clearly-marked placeholder.
6. **Repository takes the uid in its constructor** (`new FirestoreRepository(uid)`) instead of every method
   taking a uid. Same effect, less repetition; the ViewModel creates it after anonymous sign-in.
7. **Demo seed docs have fixed ids** (`demo_00`...). "Reseed demo data" overwrites them rather than
   creating duplicates, and expenses the user added are left alone.
8. **Write callbacks fire after the local commit**, not the server ack, so adding an expense offline
   returns instantly (CLAUDE.md 7). Server failures are logged.
9. Swipe-to-delete uses `confirmValueChange`, which is deprecated in this Compose version but works; noted
   for a Phase 9 cleanup.
