# RUN.md: how to build and run SpendWise (for humans and AI agents)

SpendWise is a native Android app (Kotlin + Jetpack Compose, Java backend package, Firebase, SQLite).
This file is enough to build, run, test and inspect the whole thing from a fresh clone. Commands are given for
Windows PowerShell; Git Bash / macOS / Linux equivalents are noted where they differ.

## 0. What you need

| Need | Details |
|---|---|
| JDK 17 or newer | A **full JDK** (with `jlink`), e.g. Eclipse Temurin 21. A JRE-only install fails the build (see Troubleshooting). |
| Android SDK | Platform **android-37.0**, build-tools, platform-tools, emulator. Easiest: install Android Studio. |
| An emulator or phone | Android 8.0+ (API 26+). Emulator image with Google Play services is best (location, Google Maps). |
| `app/google-services.json` | The Firebase config, **not in the repo**. Get it from the project owner and save it as `app/google-services.json`. |
| Internet | Firebase, map tiles and the OSRM route need it (the app degrades gracefully offline). |

No Maps API key is needed (the map is OpenStreetMap). No Docker, no server.

## 1. One-time setup

```powershell
git clone <repo-url> SpendWise
cd SpendWise
```
1. Put `google-services.json` in the `app/` folder (next to `app/build.gradle.kts`). Without it the app still builds but
   shows "Firebase is not configured" and the Gradle build prints a warning.
2. Tell Gradle where the SDK is. Android Studio does this for you; otherwise create `local.properties` in the project root:
   ```
   sdk.dir=C\:\\Android\\Sdk
   ```
   (or set the `ANDROID_HOME` environment variable). `local.properties.example` shows the format; never commit the real one.
3. Make sure `JAVA_HOME` points at a full JDK 17+:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"   # adjust to your install
   java -version
   ```
4. Firebase console (project owner does this once; see README "Setup"): enable **Email/Password** and **Anonymous**
   sign-in, create the Firestore database and publish `firestore.rules`.

## 2. Build, test, lint

```powershell
.\gradlew.bat assembleDebug            # builds app\build\outputs\apk\debug\app-debug.apk   (Linux/macOS: ./gradlew)
.\gradlew.bat testDebugUnitTest        # 53 unit tests; HTML report: app\build\reports\tests\testDebugUnitTest\index.html
.\gradlew.bat lintDebug                # lint
```
The first build downloads Gradle 9.7.1 and all libraries (a few minutes). Later builds take seconds.

## 3. Start an emulator and run the app

```powershell
$env:Path += ";C:\Android\Sdk\platform-tools;C:\Android\Sdk\emulator"
emulator -list-avds                                   # e.g. Pixel_8
Start-Process emulator -ArgumentList "-avd","Pixel_8","-no-snapshot","-no-audio"
adb wait-for-device                                   # then wait ~30-60 s until the home screen appears
adb shell getprop sys.boot_completed                  # prints 1 when fully booted

.\gradlew.bat installDebug                            # build + install on the connected device/emulator
adb shell am start -n com.mj.spendwise/.MainActivity  # launch
```
Real phone instead: enable Developer options > USB debugging, plug in, check `adb devices`, then `installDebug`.
No AVD yet? Android Studio > Device Manager > Create device (Pixel 8, a Google Play system image).

## 4. What you should see (smoke test)

1. **Splash**, then the **login screen** (first run, nobody signed in).
2. **Continue as guest**: Dashboard with about 50 demo expenses (about ₹26k this month, charts, insights).
3. **Login / Create an account**: an email account starts **empty** ("Welcome! Your account is empty"). Add an expense
   with the + button, or press **Load demo data**.
4. **Settings > Sign out** returns to the login screen. Signing back in restores that login's own local data.
5. Other tabs: Expenses (search, swipe-delete with undo), Analytics (3 tabs), Map (OpenStreetMap + route +
   Start navigation), Assistant (mock chatbot, `< >` shows the JSON), Alerts (bell icon).

## 5. Local SQLite databases (created automatically)

On the first launch on any device or emulator the app creates these files in its private `databases/` folder. Nothing
has to be set up by hand:

| File | Contents |
|---|---|
| `spendwise_app.db` | Registry of logins on this device: `profiles(profile_key, uid, email, is_guest, db_file, created_at, last_login_at, login_count)` |
| `spendwise_guest.db` | The guest profile, **already filled with the demo expenses** at first run (tables `expenses`, `alerts`, `budget`, `meta`) |
| `spendwise_u_<uid>.db` | One per email account, created **empty** at that account's first login; new data goes into it |

Rules: all guests share `spendwise_guest.db`; every email account has its own file; a login never sees another login's rows.
Firestore stays the source of truth; each SQLite file is a write-through copy that is shown instantly at startup.

Inspect them from a PC (the emulator has no `sqlite3` tool; the Android SDK ships `sqlite3.exe` in platform-tools):
```powershell
$sq = "C:\Android\Sdk\platform-tools\sqlite3.exe"
adb shell run-as com.mj.spendwise ls databases/
adb exec-out run-as com.mj.spendwise cat databases/spendwise_app.db > app.db
& $sq -header -column app.db "SELECT profile_key, is_guest, email, db_file, login_count FROM profiles;"
adb exec-out run-as com.mj.spendwise cat databases/spendwise_guest.db > guest.db
& $sq -header -column guest.db "SELECT COUNT(*) FROM expenses;"
& $sq -header -column guest.db "SELECT category, ROUND(SUM(amount),2) AS total FROM expenses GROUP BY category ORDER BY total DESC;"
```
`run-as` works on debug builds only. In the app: **Settings > Local database (SQLite)** shows the file name, row counts and sign-in count.

## 6. Useful commands

```powershell
adb logcat -s ExpenseViewModel:I AndroidRuntime:E     # startup timings ("Opened spendwise_guest.db: 51 expenses in 2 ms") and crashes
adb shell pm clear com.mj.spendwise                   # wipe app data = brand-new install state (databases recreated on next launch)
adb uninstall com.mj.spendwise                        # remove the app
adb shell cmd connectivity airplane-mode enable       # offline demo (disable to reconnect)
adb emu geo fix 72.9985 19.0745                       # fake GPS (longitude latitude) on an emulator
adb shell pm grant com.mj.spendwise android.permission.POST_NOTIFICATIONS   # allow notifications without the prompt
```

## 7. Project map (where to change things)

```
app/src/main/java/com/mj/spendwise/backend/   Java: Expense/AlertItem/BudgetConfig POJOs, FirestoreRepository, AuthManager,
                                              ConnectivityMonitor, LocalDatabase (per-profile SQLite), LocalProfiles, ProfileKeys
app/src/main/kotlin/com/mj/spendwise/
  navigation/   Routes, AppNavGraph            viewmodel/  Expense/Alerts/Chat/Map ViewModels
  ui/screens/   login, dashboard, expenses, analytics, map, chat, alerts, settings, scan
  ml/ ai/ notifications/ location/ data/       OCR parser, classifier, insights, anomaly, chatbot, alerts, OSM routing, demo data
app/src/main/assets/    demo_expenses.json, chat_intents.json
app/src/test/           JUnit tests            firestore.rules   security rules to publish
README.md (overview, demo script)   DECISIONS.md (why things are the way they are)
```
Constraints: Kotlin everywhere except `com.mj.spendwise.backend` (Java); no Room (plain `SQLiteOpenHelper`); Firebase
Auth + Firestore are the only cloud services; the map is OpenStreetMap (osmdroid), never Google Maps.

## 8. Troubleshooting

| Symptom | Fix |
|---|---|
| `jlink executable ... does not exist` | Gradle is using a JRE. Run `.\gradlew.bat --stop`, set `JAVA_HOME` to a full JDK 17+, rebuild. |
| `SDK location not found` | Create `local.properties` with `sdk.dir=...` or set `ANDROID_HOME`. |
| `Failed to find Platform SDK with path: platforms;android-37.0` | `sdkmanager "platforms;android-37.0"` (in `cmdline-tools\latest\bin`). |
| App says "Firebase is not configured" | `app/google-services.json` is missing; add it and rebuild. |
| Login says "This sign-in method is not enabled" | Enable Email/Password (and Anonymous) in Firebase console > Authentication > Sign-in method. |
| "Cloud unavailable" / permission denied | Publish `firestore.rules` in the Firestore console; check the internet connection. |
| `no devices/emulators found` | Start the emulator, wait for boot (`sys.boot_completed` = 1), or reconnect the phone (`adb kill-server`). |
| "Running multiple emulators with the same AVD" | An emulator is already running; use it (`adb devices`). |
| Map shows no tiles | Needs internet (OpenStreetMap). Markers and the straight-line route still show offline. |
