# SpendWise

Android expense tracker (Kotlin + Jetpack Compose, Firebase) built as a mobile-app lab mini-project.
Photograph a receipt, on-device OCR fills merchant and amount, a keyword classifier suggests a category,
and an analytics engine shows the month-end projection and unusual spending. The map uses
**OpenStreetMap (osmdroid)**, so no API key is needed.

> The lab's "Google Maps" outcome is satisfied with OpenStreetMap as a free alternative. "Start navigation"
> opens whatever maps app is installed (Google Maps included) through a standard `geo:` intent.

- Package: `com.mj.spendwise` · Min SDK 26 · compile/target SDK 37
- Kotlin for the app; **Java only** in `com.mj.spendwise.backend` (Firestore repository, POJOs, connectivity monitor)
- Local SQLite 3 database (`spendwise.db`, plain `SQLiteOpenHelper` in the Java backend package, no Room) keeps an on-device copy for instant startup; Firestore stays the source of truth and its persistent cache handles the offline write queue
- No Docker, no server: a plain Android app with Firebase (Anonymous Auth + Firestore) as the only backend

## Setup

1. **Firebase project** (console.firebase.google.com): add an Android app with package `com.mj.spendwise`,
   download `google-services.json` and put it in `app/` (it is git-ignored; `app/google-services.json.example` shows the shape).
2. **Authentication → Sign-in method → enable both Email/Password and Anonymous** (Email/Password is for the login screen, Anonymous for "Continue as guest").
3. **Firestore Database → Create database**, then paste the rules from [`firestore.rules`](firestore.rules) and publish.
4. Use an emulator or phone. **No Maps API key is needed.** (Google Play services is only needed for the location fix.)
5. Build and run:

```
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug
./gradlew testDebugUnitTest      # 46 unit tests
./gradlew lintDebug
```

Open the folder in Android Studio (recent) or run the commands above. Requires JDK 17+ and Android SDK platform 37.
Without `google-services.json` the app still builds and shows "Firebase is not configured" instead of crashing.

On first launch the app signs in anonymously and seeds about 50 demo expenses spread over the last 90 days
(dates are shifted to "now", so the charts always look real).

## Lab outcomes → where to see them

| # | Outcome | Where |
|---|---|---|
| 1 | Basic app, UI basics | Material 3 theme (light/dark), cards, lists, forms, dialogs, chips |
| 2 | Multi-screen navigation | Splash → bottom nav (5 tabs), nested graphs, arguments, bottom sheet, dialogs, top-bar actions |
| 3 | Local database (SQLite) | `LocalDatabase.java`: expenses, alerts and budget tables, written through on every cloud snapshot and read at startup (Settings shows the row counts) |
| 4 | Data sync with cloud | Firestore CRUD + realtime listener + offline queue + Sync chip |
| 5 | Network connectivity | `ConnectivityManager.NetworkCallback`, offline banner, pending count, auto-resync, "Sync on Wi-Fi only" |
| 6 | Maps navigation | OpenStreetMap: HQ + expense markers, distance/ETA, route line (OSRM), Start navigation |
| 7 | Network + multimedia + GPS | Camera/gallery receipts, FusedLocation "attach location", Firestore |
| 8 | AI chatbot | Assistant tab (mock, with "View raw JSON") |
| 9 | ML features | ML Kit OCR, keyword classifier, projection, statistical anomaly detection |
| 10 | AI alerts and notifications | Rule-based alerts → notifications + Alerts feed |

## Demo script (5 minutes)

1. Launch → splash → **Login** (sign in, create an account, or Continue as guest) → **Dashboard** with populated charts (chip: Synced ✓). Settings → Account has **Sign out**.
2. **Scan receipt → Use demo receipt** → OCR text → Domino's Pizza / ₹659 / Food pre-filled → Confirm → Save. Dashboard totals update live.
3. Show the new document in the **Firebase console**.
4. **Airplane mode on** → banner appears → add an expense → "pending" cloud icon → airplane off → *Syncing* → *Synced*.
5. **Analytics**: projection, month-over-month change, anomalies (Trends tab).
6. **Map**: expense pins + HQ pin → Walk/Drive toggle → distance/ETA → **Start navigation** opens the maps app.
7. **Assistant**: "How much did I spend on food?" → tap `< >` to show the JSON → "Take me to the office" → jumps to Map.
8. **Alerts**: adding a large expense fires a notification and an Alerts entry (or press "Trigger demo alert").

## Real vs mock (honest labels for the viva)

| Feature | Real | Mock / simulated |
|---|---|---|
| Firestore CRUD, offline cache, realtime sync | yes | |
| Connectivity detection | yes | |
| ML Kit OCR | yes (on-device) | |
| Category classifier | rule-based | not a trained model |
| Projection, month-over-month, anomaly detection | statistical | |
| GPS, distance | yes | ETA uses fixed 25 km/h (drive) / 5 km/h (walk) locally; OSRM time is used for Drive when reachable |
| Map (OpenStreetMap tiles, markers, route) | yes | HQ location is fake; the OSRM demo server is best-effort with a straight-line fallback |
| Chatbot | numbers come from your real data | JSON intent matcher with a typing delay; no AI API |
| Alerts | notifications are real | triggers are rules, not an ML model |

## Code map

```
app/src/main/java/com/mj/spendwise/backend/   Java: Expense/AlertItem/BudgetConfig POJOs, FirestoreRepository,
                                              AuthManager, ConnectivityMonitor, SyncStatus
app/src/main/kotlin/com/mj/spendwise/
  navigation/  Routes, AppNavGraph (single Scaffold + NavHost)
  viewmodel/   ExpenseViewModel (single source of truth), MapViewModel, ChatViewModel, AlertsViewModel
  ml/          ReceiptTextParser, CategoryClassifier, OcrEngine, InsightsEngine, AnomalyDetector
  ai/          MockAiEngine (+ assets/chat_intents.json)
  notifications/ NotificationHelper, AlertRules, DailyReminderWorker
  location/    LocationProvider, Geo (haversine/ETA), RouteService (OSRM)
  ui/          theme, components, screens
```

Design notes and every judgement call are in [`DECISIONS.md`](DECISIONS.md). Composables never touch Firebase; only
ViewModels talk to `FirestoreRepository`, and there is exactly one Firestore listener for expenses.

## Screenshots to capture for the report

Dashboard (light and dark) · Expenses (swipe delete + Undo) · Add expense with auto-category · Receipt scanner sheet with
parsed chips · Analytics tabs · Offline banner with a pending expense · Map with route · Chat with the JSON view ·
Alerts feed + notification shade · Settings.

## Known limits

- The OSRM demo server and OSM tile servers are free shared services: fine for a lab demo, not for production.
- The daily reminder uses a 15-minute WorkManager job (the platform minimum) and acts only after 8 PM.
- Anonymous auth means data belongs to the device's anonymous user; reinstalling creates a new user.
