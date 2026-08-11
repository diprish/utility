# Utility Meter

An Android app for recording utility meter readings. Track multiple meters
(electricity, water, gas, heat…), capture a reading straight from the camera
with on-device OCR, and see your consumption over time.

<p align="center">
  <em>Kotlin · Jetpack Compose · Room · CameraX · ML Kit</em>
</p>

## Features

- **Multiple meters** — add as many meters as you like, each with a type,
  unit and optional location. The home screen shows each meter's latest
  reading at a glance.
- **Read from a photo** — point the camera at the meter and tap the shutter.
  Google ML Kit's on-device text recognizer reads the digits and pre-fills the
  value; you confirm or correct it before saving. Works fully offline, no API
  key, no data leaves the device.
- **Manual entry** — no camera or a hard-to-read dial? Type the reading in
  directly. Each entry can carry a note.
- **Usage over time** — the meter detail screen charts consumption between
  consecutive readings, plus totals and an average-per-day figure.
- **Local & private** — everything is stored on-device in a Room (SQLite)
  database. Photos are kept in the app's private storage.

## Tech stack

| Concern            | Choice                                             |
|--------------------|----------------------------------------------------|
| Language           | Kotlin                                             |
| UI                 | Jetpack Compose + Material 3 (dynamic color)       |
| Architecture       | MVVM — `ViewModel` + `StateFlow`, single repository |
| Persistence        | Room                                               |
| Camera             | CameraX                                            |
| OCR                | ML Kit Text Recognition (on-device)                |
| Navigation         | Navigation-Compose                                 |
| Min / target SDK   | 26 / 35                                            |

## Project layout

```
app/src/main/java/com/diprish/utilitymeter/
├── UtilityMeterApp.kt          # Application + tiny service locator
├── MainActivity.kt             # Single-activity host
├── data/                       # Room entities, DAO, database, repository
│   ├── Meter.kt / MeterReading.kt / MeterType.kt
│   ├── MeterDao.kt / AppDatabase.kt / MeterRepository.kt
├── ocr/
│   └── MeterOcr.kt             # ML Kit wrapper + number-extraction heuristic
└── ui/
    ├── meters/                 # Meter list + add-meter dialog
    ├── detail/                 # Meter detail + usage chart
    ├── reading/                # Camera capture, OCR, add-reading flow
    ├── components/UsageChart.kt# Dependency-free Compose bar chart
    ├── navigation/NavGraph.kt
    └── theme/
```

## Building

Open the project in a recent **Android Studio** (Ladybug or newer) and run the
`app` configuration, or from the command line:

```bash
# Point the build at your Android SDK (or set sdk.dir in local.properties)
export ANDROID_HOME=/path/to/android-sdk

./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run unit tests
./gradlew installDebug         # install on a connected device/emulator
```

The resulting APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

> Note: the debug APK is fairly large (~60 MB) because it bundles the ML Kit
> on-device text-recognition model. A release build with resource shrinking is
> considerably smaller.

## How OCR works

`MeterOcr.recognize()` runs ML Kit's Latin text recognizer over the captured
JPEG, then applies a small heuristic (`extractCandidates` / `pickBest`) to pull
the most reading-like number out of the recognized text: it collects every
numeric token, normalizes separators and stray spaces, and prefers the token
with the most digits. The recognized value is always shown for confirmation
before it is saved, so a mis-read never silently corrupts your history. The
pure parsing logic is covered by unit tests in
`app/src/test/.../MeterOcrTest.kt`.

## Ideas for later

- Reminders / notifications to take a reading on a schedule
- CSV export and cloud backup
- Cost estimation from a per-unit tariff
- Cropping the photo to the digit window before OCR for higher accuracy
