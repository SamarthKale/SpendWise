# CLAUDE.md — SpendWise (Mobile App Lab Mini-Project)

> Build spec for Claude Code. Read this whole file first, then work through **Section 11 (Build Phases)** in order. Do not skip acceptance checks. Ask the human only for things listed in **Section 15**.

---

## 1. Project summary

**SpendWise** is an Android expense tracker for students / young professionals. The user photographs a receipt, on-device OCR fills merchant + amount, a classifier suggests a category, and an analytics engine shows month-end projections and anomalies. All cloud data lives in **Firebase**. The app also has a mock AI chatbot, Google Maps navigation to a (fake) company office, and mock AI-style alerts.

This is a **lab presentation project**. Priorities, in order:
1. Everything demos smoothly and never crashes.
2. Every lab outcome in Section 3 is visibly demonstrable.
3. Code is clean, readable, and easy to explain in a viva.

Do **not** over-engineer. Mocked features must *look* real but may be rule/JSON driven.

---

## 2. Hard constraints

| Rule | Detail |
|---|---|
| Language | **Kotlin** for the entire app: UI, navigation, ViewModels, ML, maps, chatbot, notifications. |
| Backend layer | **Java** for the "backend" package only (`com.spendwise.backend`): Firestore repository, POJO models, connectivity monitor. Kotlin calls into it. *(Interpretation: "Kotlin + backend Java". If the human says all-Kotlin, port this package to Kotlin — nothing else changes.)* |
| UI | Jetpack Compose + Material 3. Single Activity, Navigation Compose. |
| Local DB | **Do NOT use Room/SQLite.** Lab outcome 3 is intentionally skipped. |
| Cloud | **Firebase only**: Anonymous Auth + Cloud Firestore. No custom server, no REST backend, no Cloud Functions. |
| Offline | Use **Firestore's built-in persistent cache** as the offline/sync mechanism (this is the "data synchronization" story). |
| Min SDK | 26. Target/compile SDK = latest stable. |
| Build | Gradle Kotlin DSL + version catalog (`libs.versions.toml`). Pick current stable versions of all libraries. |
| Secrets | Maps API key in `local.properties`, injected via Secrets Gradle Plugin. Never commit keys or `google-services.json`. |
| Currency | ₹ (INR), locale `en-IN`. |

---

## 3. Lab outcome → feature map

| # | Lab outcome | Status | Where it is shown in the app |
|---|---|---|---|
| 1 | Basic mobile app with UI basics | ✅ | Material 3 theme, cards, lists, forms, dialogs, chips |
| 2 | Multi-screen navigation + UI components | ✅ | Bottom nav + nested graphs + argument passing + bottom sheet + nav drawer/top-bar actions (Sec. 8) |
| 3 | Local database (SQLite/Room) | ⛔ **SKIPPED** | Replaced by Firestore persistent cache |
| 4 | Data sync with Firebase/Cloud | ✅ | Firestore CRUD + realtime listeners + offline queue + Sync-status chip |
| 5 | Network connectivity for data exchange | ✅ | `ConnectivityManager.NetworkCallback`, offline banner, pending-writes counter, auto-resync |
| 6 | Google Maps navigation | ✅ | Map screen: fake company HQ marker, expense markers, "Navigate" via Google Maps intent, in-app route line + distance/ETA |
| 7 | Interactive app with network + multimedia + GPS | ✅ | Camera/gallery receipt capture (multimedia), FusedLocation (GPS), Firestore (network) |
| 8 | AI chatbot | ✅ (mock) | Assistant screen, canned JSON responses, "View JSON" toggle |
| 9 | ML-based features | ✅ | ML Kit on-device OCR (real) + keyword classifier + z-score anomaly detection + linear projection |
| 10 | AI-based alerts & notifications | ✅ (mock/rule-based) | Budget/anomaly/projection notifications + in-app Alerts feed |

---

## 4. Tech stack & dependencies

Add via version catalog (use latest stable of each):

- Kotlin, Android Gradle Plugin, Compose BOM, Material 3
- `androidx.navigation:navigation-compose`
- `androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`
- Firebase BoM → `firebase-auth`, `firebase-firestore` (Java-friendly, no `-ktx` needed)
- `com.google.mlkit:text-recognition` (bundled model, works offline)
- `com.google.maps.android:maps-compose`, `com.google.android.gms:play-services-maps`, `play-services-location`
- `androidx.work:work-runtime-ktx` (scheduled mock alerts)
- `kotlinx-serialization-json` **or** plain `org.json` for chatbot JSON (prefer `org.json` — fewer moving parts)
- `io.coil-kt:coil-compose` (receipt thumbnails)
- `androidx.activity:activity-compose` (photo picker + permissions)
- Plugins: `com.google.gms.google-services`, `com.google.android.libraries.mapsplatform.secrets-gradle-plugin`

---

## 5. Project structure

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   ├── chat_intents.json          # mock chatbot brain
│   └── demo_expenses.json         # seed data (~45 expenses over 3 months)
├── java/com/spendwise/backend/    # ← JAVA (backend layer)
│   ├── Expense.java               # Firestore POJO (no-arg ctor + getters/setters)
│   ├── AlertItem.java             # POJO for alert history
│   ├── BudgetConfig.java          # POJO
│   ├── FirestoreRepository.java   # CRUD + snapshot listeners, callbacks + LiveData
│   ├── AuthManager.java           # anonymous sign-in
│   ├── ConnectivityMonitor.java   # NetworkCallback -> LiveData<Boolean>
│   └── SyncStatus.java            # enum: SYNCED, SYNCING, OFFLINE
└── kotlin/com/spendwise/          # ← KOTLIN (everything else)
    ├── MainActivity.kt
    ├── SpendWiseApp.kt            # Application: init Firebase + Firestore settings + notif channels
    ├── Constants.kt               # HQ_LAT, HQ_LNG, categories, colors
    ├── navigation/
    │   ├── Routes.kt
    │   └── AppNavGraph.kt
    ├── ui/
    │   ├── theme/                 # Color, Type, Theme
    │   ├── components/            # ExpenseCard, CategoryChip, SyncChip, OfflineBanner, BarChart, EmptyState
    │   └── screens/
    │       ├── splash/SplashScreen.kt
    │       ├── dashboard/DashboardScreen.kt
    │       ├── expenses/ExpenseListScreen.kt
    │       ├── expenses/ExpenseDetailScreen.kt
    │       ├── expenses/AddEditExpenseScreen.kt
    │       ├── scan/ReceiptScannerSheet.kt
    │       ├── analytics/AnalyticsScreen.kt
    │       ├── map/MapScreen.kt
    │       ├── chat/ChatScreen.kt
    │       ├── alerts/AlertsScreen.kt
    │       └── settings/SettingsScreen.kt
    ├── viewmodel/                 # ExpenseViewModel, ChatViewModel, AlertsViewModel, MapViewModel
    ├── ml/
    │   ├── ReceiptTextParser.kt
    │   ├── CategoryClassifier.kt
    │   ├── InsightsEngine.kt
    │   └── AnomalyDetector.kt
    ├── ai/
    │   ├── MockAiEngine.kt        # loads chat_intents.json, matches intent, fills templates
    │   └── ChatModels.kt
    ├── notifications/
    │   ├── NotificationHelper.kt
    │   ├── AlertRules.kt
    │   └── DailyReminderWorker.kt
    └── location/
        └── LocationProvider.kt    # FusedLocationProviderClient wrapper
```

---

## 6. Data model & Firestore layout

### 6.1 Expense (Java POJO in `backend/Expense.java`)

| Field | Type | Notes |
|---|---|---|
| `id` | String | Firestore doc id (set from snapshot, `@Exclude` from write if you prefer) |
| `merchant` | String | |
| `category` | String | One of: Food, Groceries, Fuel, Shopping, Travel, Bills, Entertainment, Health |
| `amount` | double | INR |
| `notes` | String | optional |
| `locationName` | String | optional, human readable |
| `latitude`, `longitude` | Double (nullable) | optional; from GPS at capture |
| `timestamp` | `com.google.firebase.Timestamp` | default `Timestamp.now()` |
| `source` | String | `"manual"` or `"receipt_scan"` |

Must have a **public no-arg constructor** and public getters/setters (Firestore requirement).

### 6.2 Firestore collections

```
users/{uid}/expenses/{expenseId}     -> Expense
users/{uid}/alerts/{alertId}         -> AlertItem {title, body, type, severity, payloadJson, createdAt, read}
users/{uid}/settings/budget          -> BudgetConfig {monthlyBudget, categoryBudgets: map<String,double>}
```

`uid` = Firebase Anonymous Auth uid (zero-friction login for demo).

### 6.3 Security rules (put in `firestore.rules` and tell human to paste in console)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```

---

## 7. Backend layer (Java) — requirements

**`SpendWiseApp.kt` init** (Kotlin) must enable persistent offline cache:
```java
FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
    .build();
FirebaseFirestore.getInstance().setFirestoreSettings(settings);
```
(Wrap in a static helper in `FirestoreRepository.java` and call it from the Application class.)

**`AuthManager.java`**
- `signInAnonymouslyIfNeeded(Callback<String> onUid, Callback<Exception> onError)`
- Reuse existing `currentUser` if present.

**`FirestoreRepository.java`** — all methods take a uid from `AuthManager`.
- `LiveData<List<Expense>> observeExpenses()` — `addSnapshotListener` ordered by `timestamp` desc, `includeMetadataChanges = true`.
- `LiveData<SyncStatus> observeSyncStatus()` — derived from snapshot metadata: `hasPendingWrites()` → `SYNCING`, `isFromCache()` and offline → `OFFLINE`, else `SYNCED`. Combine with `ConnectivityMonitor`.
- `LiveData<Integer> observePendingWriteCount()` — count docs where `metadata.hasPendingWrites()`.
- `addExpense(Expense, Callback<String> ok, Callback<Exception> err)` — **do not block UI on the server ack when offline**; the write is queued. Fire success callback optimistically after local commit (use `set()` and don't await the server task for UI navigation; log failure).
- `updateExpense`, `deleteExpense`
- `observeAlerts()`, `addAlert(AlertItem)`, `markAlertRead(id)`
- `getBudget(Callback)`, `saveBudget(BudgetConfig)`
- `seedDemoData(List<Expense>)` — batched write, guarded by a `seeded` flag in `settings/meta`.

**`ConnectivityMonitor.java`**
- Registers `ConnectivityManager.registerDefaultNetworkCallback`.
- Exposes `LiveData<Boolean> isOnline`, plus `LiveData<String> networkType` (`"Wi-Fi"`, `"Mobile data"`, `"Offline"`) using `NetworkCapabilities.TRANSPORT_*`.
- Unregisters in `onCleared()` / when app scope ends.

Kotlin ViewModels convert LiveData → `StateFlow`/Compose state with `observeAsState()` or `asFlow()`.

---

## 8. Navigation & screens

### 8.1 Patterns to demonstrate (lab outcome 2 — make each obvious)

1. **Splash → Main** with `popUpTo` so Back exits the app.
2. **Bottom navigation bar** (5 tabs): Dashboard · Expenses · Analytics · Map · Assistant.
3. **Nested nav graphs**: `main_graph` (tabs) and `expense_flow` (Add/Edit + Detail).
4. **Navigation with arguments**: `expense_detail/{expenseId}`, `add_expense?scan={scan}`.
5. **Modal bottom sheet**: Receipt Scanner.
6. **Dialogs**: delete-confirm `AlertDialog`; budget-edit dialog.
7. **Top app bar actions**: bell icon (→ Alerts, with unread badge), settings icon (→ Settings).
8. **FAB** on Expenses/Dashboard → Add Expense.
9. **Tabs** (`TabRow`) on Analytics: *This Month / Categories / Trends*.
10. **Nav drawer is optional** — skip unless time permits.

### 8.2 Routes (`Routes.kt`)

```
splash
main/dashboard | main/expenses | main/analytics | main/map | main/chat
add_expense?scan={scan}&editId={editId}
expense_detail/{expenseId}
alerts
settings
```

### 8.3 Screen specs

| Screen | Must contain |
|---|---|
| **Splash** | Logo, app name, 1.2 s delay while anonymous sign-in + seeding completes, then navigate. |
| **Dashboard** | Greeting; **SyncChip** (Synced ✓ / Syncing 2 pending / Offline) + network type; month total vs budget progress bar; projected month-end card; **real** weekly spending bar chart (aggregate by day-of-week — no placeholders); top 3 categories; 1–2 AI insight cards; recent 5 expenses. |
| **Expenses** | Search field, category filter chips, list grouped by date, swipe-to-delete (with undo snackbar), tap → detail. |
| **Add/Edit** | Merchant, amount (numeric), category dropdown (auto-selected by classifier as user types merchant), notes, date picker, "Attach current location" toggle (GPS), **Scan receipt** button opening the sheet. |
| **Receipt Scanner (sheet)** | Buttons: *Camera*, *Gallery*, *Use demo receipt* (bundled sample image for guaranteed demo). Shows image preview, raw OCR text (collapsible), parsed merchant/amount/category chips; **Confirm** pre-fills Add screen. |
| **Expense detail** | All fields, mini map if lat/lng exists, edit/delete. |
| **Analytics** | Tab 1: projection + MoM % change. Tab 2: category breakdown (bars + %). Tab 3: 3-month trend line/bars + anomaly list. |
| **Map** | See 9.5. |
| **Assistant** | See 9.6. |
| **Alerts** | List of AI alerts (newest first), severity icon, unread dot, tap to expand and show payload JSON. "Trigger demo alert" button. |
| **Settings** | Monthly budget edit, category budgets, notification toggle, "Send test notification", "Reseed demo data", app version, Firebase uid (for debugging). |

Empty states and loading states are required on every list screen. Use `collectAsStateWithLifecycle`.

---

## 9. Feature specs

### 9.1 Firebase sync (LO 4)
- On first launch: anonymous sign-in → seed `demo_expenses.json` (dates should be relative to *now*: spread over the last ~90 days so analytics look real regardless of when the demo happens; the seed loader must shift timestamps at load time).
- All screens read from the single realtime listener in `ExpenseViewModel` (single source of truth).
- **Demo-critical behaviour:** turn on airplane mode → add an expense → it appears instantly in the list with a "pending" indicator → turn network back on → chip changes to Syncing → Synced. Verify this works.

### 9.2 Network connectivity (LO 5)
- `OfflineBanner` at top of main scaffold when offline: "You're offline — changes will sync automatically."
- `SyncChip` in Dashboard header and a smaller variant in the top bar.
- Show network type (Wi-Fi / Mobile data).
- Settings has a "Sync on Wi-Fi only" toggle (UI + stored in `settings/budget` doc or DataStore-free `SharedPreferences`); if enabled and on mobile data, call `FirebaseFirestore.disableNetwork()`, re-enable on Wi-Fi. Keep the implementation ≤ 30 lines.

### 9.3 Expense capture: multimedia + GPS + ML (LO 7, 9)
**Receipt pipeline (real, on-device):**
`Image (camera/gallery) → ML Kit TextRecognition → ReceiptTextParser → CategoryClassifier → user confirms → Firestore`

- Camera: use `ActivityResultContracts.TakePicture` with a `FileProvider` URI (declare provider in manifest). Gallery: `PickVisualMedia`.
- `ReceiptTextParser.parse(rawText): ParsedReceipt(merchant: String?, amount: Double?)`
  - Merchant = first non-empty line in the top 5 lines that isn't purely numeric/date/address-like.
  - Amount = prefer the number on a line containing `total|grand total|amount due|net payable`; else the largest currency-like number. Handle `₹`, `Rs.`, `INR`, commas, and `.00`.
- `CategoryClassifier.classify(merchant, notes): String?` — keyword map (lower-cased) for the 8 categories. Include Indian brands/keywords: Swiggy, Zomato, Domino's, McDonald's, Starbucks, Cafe, Restaurant → Food; BigBasket, D-Mart, Reliance Fresh, Blinkit, Zepto → Groceries; HP, Indian Oil, Bharat Petroleum, Shell, Petrol, Fuel → Fuel; Amazon, Flipkart, Myntra, Zara → Shopping; Uber, Ola, IRCTC, MakeMyTrip, Metro, Rapido → Travel; Electricity, Airtel, Jio, Vi, Recharge, Bill → Bills; Netflix, PVR, BookMyShow, Spotify → Entertainment; Apollo, Pharmacy, MedPlus, Clinic, Hospital → Health. Return `null` if no match; UI then defaults to "Shopping" but shows "Suggested: —".
- Both classes keep **stable function signatures** so a trained model can replace them later (mention in comments and viva notes).
- **GPS:** `LocationProvider.getCurrentLocation()` using `FusedLocationProviderClient.getCurrentLocation(PRIORITY_BALANCED_POWER_ACCURACY, ...)`. Reverse-geocode with `Geocoder` (best effort, offline-safe try/catch) into `locationName`.
- Handle every permission denial gracefully (rationale text + continue without it).

### 9.4 Analytics & ML (LO 9)
`InsightsEngine` (pure Kotlin, unit-testable, takes `List<Expense>` + `now`):
- `monthTotal(month)`, `previousMonthTotal`
- `projectedMonthEnd()` = `spentSoFar / daysElapsed * daysInMonth` (guard `daysElapsed == 0`)
- `monthOverMonthPercent()` (guard divide-by-zero → return `null`)
- `categoryTotals(month)`, `weeklyByDayOfWeek(last 7 days)` (Mon–Sun totals)
- `generateInsights(): List<Insight>` — human-readable strings, e.g. "Food is 42% higher than your 3-month average", "At this pace you'll spend ₹X, ₹Y over budget".

`AnomalyDetector`:
- For each category: compute mean and std-dev of monthly totals over previous 3 months (or of per-transaction amounts). Flag current month if `> mean + 2σ` or `> 1.5 × mean` when σ is tiny. Also flag single transactions `> mean + 3σ` of that category's history.
- Output `Anomaly(category, currentValue, expectedValue, severity)`.

Label the UI honestly: "On-device ML: OCR (ML Kit), keyword classifier, statistical anomaly detection."

### 9.5 Google Maps navigation (LO 6, 7)
**Fake company location** — defined once in `Constants.kt`:
```kotlin
const val HQ_NAME = "SpendWise HQ (Demo Office)"
const val HQ_ADDRESS = "Sector 9A, Vashi, Navi Mumbai, Maharashtra 400703"
const val HQ_LAT = 19.0745   // approximate — human may tweak
const val HQ_LNG = 72.9985
```
Map screen behaviour:
1. `GoogleMap` (Maps Compose) with: HQ marker (distinct colour, info window "Visit us / Get directions"), **expense markers** (only those with lat/lng, colored by category, info window shows merchant + ₹amount).
2. "My location" blue dot (permission-gated) via `isMyLocationEnabled`.
3. **Directions card** (bottom sheet-like) with HQ name, address, and:
   - Live **distance** (haversine from current location) and **ETA** (assume 25 km/h driving, 5 km/h walking; toggle Walk/Drive) — computed locally, no Directions API.
   - **In-app route**: draw a `Polyline` from user → HQ (straight line or a small hardcoded waypoint list if location unavailable; if no GPS/permission, fall back to a default start point ~2 km away so the demo always shows a route).
   - **"Start navigation"** button → launch Google Maps app:
     ```kotlin
     val uri = Uri.parse("google.navigation:q=$HQ_LAT,$HQ_LNG&mode=d")   // mode=w for walking
     val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
     // fallback: geo:$HQ_LAT,$HQ_LNG?q=... or https://www.google.com/maps/dir/?api=1&destination=...
     ```
4. Filter chips: *All / This month / By category*.
5. If Maps fails to load (bad key / emulator without Play Services), show a friendly placeholder card with the "Start navigation" button still working.

### 9.6 Mock AI chatbot (LO 8)
Goal: looks like an LLM assistant; actually answers from `assets/chat_intents.json` + real numbers from the user's Firestore data. **No network calls to any AI API.**

**Pre-seeded conversation** shown on first open (3 messages: greeting, "Try asking…" suggestion chips, sample question/answer).

**Response contract (every bot reply is a JSON object; UI parses it):**
```json
{
  "intent": "category_spend",
  "confidence": 0.93,
  "reply": "You've spent ₹{amount} on {category} this month — {pct}% of your total.",
  "data": { "category": "Food", "amount": 3240, "period": "month" },
  "action": { "type": "NONE" },
  "suggestions": ["Show top category", "Set a Food budget", "Will I exceed my budget?"]
}
```
`action.type` ∈ `NONE | NAVIGATE_MAP | OPEN_ALERTS | OPEN_ANALYTICS`; when present the UI shows a button chip that navigates.

**`chat_intents.json` schema**
```json
{ "intents": [
  { "name": "total_spend",
    "keywords": ["how much", "total", "spent so far", "spending"],
    "template": "So far this month you've spent ₹{monthTotal}.",
    "action": "NONE",
    "suggestions": ["Break it down by category", "What's my projection?"] }
] }
```
Required intents: `greeting`, `total_spend`, `category_spend` (extract category from text), `top_category`, `projection`, `budget_status`, `anomaly_check`, `saving_tips`, `compare_last_month`, `navigate_hq` (→ `NAVIGATE_MAP`, "Sure! Here's how to get to our office."), `help`, `fallback`.

**`MockAiEngine.respond(userText, insights): ChatReply`**
1. Lower-case, tokenize.
2. Score each intent by keyword hits (longest phrase match wins); `confidence = min(0.99, 0.55 + 0.15 * hits)`; below 0.6 → `fallback`.
3. Fill `{placeholders}` from `InsightsEngine` values.
4. Build the response JSON with `org.json.JSONObject`, then **parse it back** into `ChatReply` (so the JSON path is genuinely exercised).
5. Add 600–1200 ms fake latency + typing indicator ("SpendWise AI is thinking…").

**UI**: chat bubbles (user right / bot left), quick-suggestion chips, action buttons, auto-scroll. A small `{ }` icon on each bot message toggles **"View raw JSON"** (monospace card) — this is the presentation hook. Header subtitle: "AI Assistant (demo)". Persist chat only in memory (ViewModel).

### 9.7 AI-based alerts & notifications (LO 10)
Rule-based "AI" alerts, generated by `AlertRules` after every expense add/update and by a periodic worker:

| Rule | Trigger | Severity |
|---|---|---|
| Budget 80% | month total ≥ 80% of monthly budget | warning |
| Budget exceeded | ≥ 100% | critical |
| Category anomaly | `AnomalyDetector` flags a category | warning |
| Projection overshoot | projected month-end > budget | info |
| Big-ticket transaction | single expense > mean + 3σ | info |
| Daily reminder | no expense logged today by 8 PM (WorkManager periodic, 15-min min interval OK for demo) | info |

- Each fired alert: (a) saved to `users/{uid}/alerts` (so it syncs), (b) shown as a system notification via `NotificationHelper` (channel `spendwise_alerts`, high importance; tap → deep link to Alerts screen), (c) appears in the in-app Alerts feed with payload JSON.
- **De-duplicate**: keep a `dedupeKey` (rule + month) so the same alert doesn't spam.
- Android 13+: request `POST_NOTIFICATIONS` at runtime (ask contextually, e.g., on first budget set or in Settings).
- **Demo shortcuts**: Settings → "Send test notification"; Alerts → "Trigger demo alert" (fires a realistic anomaly alert immediately). Label all as "AI-generated (demo rules)".

---

## 10. Permissions & manifest

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-feature android:name="android.hardware.camera" android:required="false"/>
<meta-data android:name="com.google.android.geo.API_KEY" android:value="${MAPS_API_KEY}"/>
<!-- FileProvider for camera captures; Application class = .SpendWiseApp -->
```

---

## 11. Build phases (do in order; commit after each)

**Phase 0 — Scaffold.** New Compose project, package `com.spendwise`, version catalog, plugins, theme, empty `MainActivity`. *Accept:* app builds and launches.

**Phase 1 — Navigation shell (LO 1, 2).** Routes, splash, bottom nav with 5 placeholder screens, top bar with bell/settings, nested graph, argument routes. *Accept:* every pattern in 8.1 is reachable.

**Phase 2 — Firebase + Java backend (LO 4).** `google-services.json` (human), Auth, POJOs, repository, persistent cache, seeding, `ExpenseViewModel`. Expenses screen lists live cloud data; add/delete works. *Accept:* changes appear in Firebase console within seconds.

**Phase 3 — Connectivity (LO 5).** `ConnectivityMonitor`, OfflineBanner, SyncChip, pending count. *Accept:* the airplane-mode demo in 9.1 works end-to-end.

**Phase 4 — Capture + ML (LO 7, 9).** Receipt sheet, ML Kit OCR, parser, classifier, GPS attach, demo receipt image. *Accept:* scanning the demo receipt pre-fills merchant/amount/category correctly.

**Phase 5 — Analytics (LO 9).** `InsightsEngine`, `AnomalyDetector`, dashboard + analytics screens with **no hardcoded numbers**, real weekly chart. *Accept:* adding an expense changes the dashboard numbers instantly.

**Phase 6 — Maps (LO 6, 7).** Map screen, HQ marker, expense markers, distance/ETA, polyline, Google Maps intent with fallbacks. *Accept:* "Start navigation" opens Google Maps to the HQ coordinates; works with location denied.

**Phase 7 — Chatbot (LO 8).** `chat_intents.json`, `MockAiEngine`, chat UI, JSON view, actions. *Accept:* all 12 intents answer; "take me to the office" navigates to Map.

**Phase 8 — Alerts (LO 10).** Rules, notifications, Alerts feed, worker, Settings test buttons. *Accept:* adding a large Food expense fires an anomaly/budget notification and an Alerts entry.

**Phase 9 — Polish.** Empty/loading/error states, dark mode, icons, app icon, no crashes on rotation, no ANRs, remove dead code, README with run instructions and screenshots list. *Accept:* cold-start demo run-through (Section 12) passes twice in a row.

Add a handful of JUnit tests for `ReceiptTextParser`, `CategoryClassifier`, `InsightsEngine` (projection + zero-division guard), and `AnomalyDetector`.

---

## 12. Presentation demo script (must work flawlessly)

1. Launch → splash → Dashboard with populated charts (sync chip: Synced).
2. Tap **Scan receipt** → *Use demo receipt* → OCR text appears → merchant/amount/category pre-filled → Confirm → Save. Dashboard totals update live.
3. Open **Firebase console** on the projector: the new document is there.
4. Switch on **airplane mode** → banner appears → add an expense → shows "pending" → airplane mode off → Syncing → Synced.
5. **Analytics** tab: projection, MoM change, anomaly flagged.
6. **Map** tab: expense pins + HQ pin → Walk/Drive toggle → distance/ETA → **Start navigation** opens Google Maps.
7. **Assistant** tab: ask "How much did I spend on food?" → answer → tap `{ }` to reveal JSON → ask "Take me to the office" → jumps to Map.
8. **Alerts**: notification fired from step 2/4 shown in the shade and the in-app feed; press "Trigger demo alert" if needed.

---

## 13. Real vs mock (be honest in the viva)

| Feature | Real | Mock / simulated |
|---|---|---|
| Firestore CRUD, offline cache, realtime sync | ✅ | |
| Connectivity detection | ✅ | |
| ML Kit OCR | ✅ | |
| Category classifier | ✅ rule-based | Not a trained model |
| Projection, MoM, anomaly detection | ✅ statistical | |
| GPS, distance | ✅ | ETA uses fixed average speeds |
| Google Maps view + markers + Start navigation intent | ✅ | Company location is fake; in-app route line is straight/hardcoded (no Directions API) |
| Chatbot | | ✅ JSON intent matcher with typing delay; numbers come from real data |
| AI alerts | ✅ notifications are real | Triggers are rule-based, not an ML model |

---

## 14. Code rules & pitfalls

- **Kotlin** in `kotlin/`, **Java** only in `java/com/spendwise/backend/`. No Room, no Retrofit, no other backends.
- MVVM: composables never touch Firebase; only ViewModels talk to `FirestoreRepository`.
- One realtime listener for expenses, shared through the ViewModel (avoid listener-per-screen).
- Money: store as `double` for simplicity; format with `NumberFormat.getCurrencyInstance(Locale("en","IN"))`.
- Never block the main thread; ML Kit and Geocoder calls must be async/off-main.
- Wrap Geocoder, Maps, ML Kit, and camera code in try/catch with user-friendly fallbacks.
- `Timestamp` ↔ `LocalDate` conversions use `ZoneId.systemDefault()`.
- Firestore `toObject(Expense.class)` needs the no-arg ctor; set `id` manually from `doc.getId()`.
- Don't ship real API keys in the repo; provide `local.properties.example` with `MAPS_API_KEY=YOUR_KEY_HERE`.
- Composable previews for the main reusable components.
- Keep each file focused (<300 lines). Comment the "why" for anything non-obvious — the author must explain it in a viva.
- Use stable Compose APIs; if an API is experimental, opt in narrowly and note it.
- Handle process death & rotation (state hoisted in ViewModels, `rememberSaveable` for form fields).

---

## 15. Manual steps for the human (Claude Code cannot do these)

1. Create a Firebase project → add Android app `com.spendwise` → download **`google-services.json`** into `app/`.
2. Firebase console → **Authentication → Sign-in method → enable Anonymous**.
3. Firebase console → **Firestore Database → Create database** → paste the rules from Section 6.3.
4. Google Cloud console → create a **Maps SDK for Android** API key → put `MAPS_API_KEY=...` in `local.properties`.
5. Use an emulator image **with Google Play services** (or a real device) so Maps, Location, and Google Maps navigation work.
6. Provide a sample receipt photo as `app/src/main/res/drawable/demo_receipt.jpg` (or let Claude Code generate a synthetic one with clear "TOTAL ₹xxx" text).

---

## 16. Definition of done

- [ ] Builds cleanly, no lint errors that break the build, runs on emulator and a physical device.
- [ ] Every lab outcome in Section 3 is demonstrable in under 5 minutes.
- [ ] Zero hardcoded dashboard/analytics numbers.
- [ ] Airplane-mode sync demo works.
- [ ] No crash when any permission is denied.
- [ ] Unit tests pass.
- [ ] README lists setup steps from Section 15 and the demo script from Section 12.