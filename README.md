# Deadline!

**Deadline!** is a campus-themed casual tile-matching Android game developed for **COMP90018 Mobile Computing Systems Programming**.

## Project Information

- **Course:** COMP90018 Mobile Computing Systems Programming
- **Group:** T01/05 - 03
- **Platform:** Android
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM
- **Minimum Android version:** Android 8.0 (API 26); target and compile SDK 34

## Project Overview

Deadline! turns familiar university experiences such as assignments, lecture notes, textbooks, code files, quizzes, coffee, deadlines, and semester stress into gameplay mechanics.

The core gameplay loop is:

**Select an available tile → Add it to the Task Tray → Complete three-tile matches → Manage Stress and recovery items → Clear the level → Record and improve the Personal Best**

Players select available tiles from a layered board and move them into a seven-slot Task Tray. When three identical tiles are collected, they are automatically matched and removed.

## Features

### Gameplay

- **Layered board:** tiles overlap across several layers. Only tiles that no higher tile covers can be selected, and covered tiles are shown dimmed.
- **Seven-slot Task Tray:** each selected tile moves into the tray. Three identical tiles are matched and removed automatically.
- **Win and loss:** clear every tile to win. The level is lost when the tray fills up with no match.
- **Undo and Restart:** multi-step undo of tile selections, and a restart that resets the level.
- **Eleven campus tile types:** including Book, Coffee, Laptop, Assignment, Quiz, Reading, Music, Exam, Presentation and Lecture Slide.
- **Stress System:** stress builds up with active play time, never from taps. A High Stress warning appears near the top of the bar. At Maximum Stress, taps may slip onto a neighbouring selectable tile.
- **Coffee Recovery:** clearing a Coffee triple removes stress. The amount depends on the Semester Week.
- **Music Recovery:** clearing a Music triple removes a little stress and slows stress for a while.
- **Completion Timer and Personal Best:** each level records its fastest completion time.

### Levels

- **Six levels for a 12-week semester:** each level covers two Semester Weeks, and difficulty increases from week to week.
- **Procedural generation:** a new board is generated every time a level starts, so replays differ.
- **Solvability validation:** every generated board is checked by a solver before it is played. If no solvable board is found, a verified fixed level is used instead.
- **Week unlocks:** clearing a level unlocks the next Semester Week.

### Sensors and feedback

- **Shake-to-Shuffle** (accelerometer): a firm shake shuffles the remaining tiles on the board.
- **Tilt-to-Peek** (rotation vector sensor): tilting the phone fades the upper layers so the tiles underneath can be seen.
- **Auto brightness** (ambient light sensor): adjusts the game screen's brightness to the surrounding light. Off by default.
- **Haptic feedback** for selecting, matching, shuffling, winning, losing and reaching Maximum Stress.
- **Background music and sound effects.**
- Every motion feature and every sound or vibration can be switched off in Settings, and every essential action is also available by tapping.

### Online features (Firebase)

- **Anonymous sign-in:** every installation gets an identity with no sign-up step.
- **Optional accounts:** email and password registration and login from **Settings → Account**. See [user accounts](docs/user-accounts.md).
- **Live per-level leaderboard** in Cloud Firestore, protected by [Security Rules](docs/firestore-security.md) that reject implausible times.
- **Cross-device progress sync**, plus one-time transfer codes to move progress to another phone.
- **Remote Config:** gameplay balance (stress, shake and tilt tuning) can be adjusted without a new release. See [Remote Config](docs/remote-config.md).
- **Offline-first:** every level is fully playable without a network. Progress is saved locally first, and results are uploaded when the device reconnects.

## Tech Stack

- Kotlin, Jetpack Compose, Material 3, Navigation Compose
- MVVM with `ViewModel` and `StateFlow`, Kotlin Coroutines
- Android `SensorManager`: accelerometer, rotation vector sensor and ambient light sensor
- Android haptic feedback, `SoundPool` and `MediaPlayer`
- Jetpack DataStore (Preferences)
- Firebase Authentication, Cloud Firestore and Firebase Remote Config
- JUnit 4, kotlinx-coroutines-test, Compose UI testing, Firebase Local Emulator Suite
- Gradle Kotlin DSL, ktlint, Android Lint, GitHub Actions

## Architecture

The application follows an MVVM-based architecture with clear separation between presentation, domain logic, sensors, persistence, and cloud services.

```text
User Input
    |
    v
Compose UI
    |
    v
ViewModel
    |
    +------> Domain / Game Engine ------> Game State
    |
    +------> Repository Interfaces
                  |
                  v
             Data Layer
          /              \
     DataStore          Firebase


Sensor Input
    |
    v
Sensor Processing
    |
    v
ViewModel
    |
    v
Game Engine
```

The Game Engine and domain logic are designed to remain independent of Android APIs so that gameplay rules can be tested using JVM unit tests.

Repository interfaces separate the domain layer from DataStore and Firebase implementations, allowing data sources to be replaced with test doubles during testing.

## Project Structure

```text
app/src/main/java/com/comp90018/deadline/
|
|-- app/                  Application class and AppContainer (manual dependency wiring)
|
|-- core/
|   |-- audio/            Background music and sound effects
|   |-- dispatcher/
|   |-- theme/            Material 3 theme, colours, typography, spacing
|   |-- ui/               Shared components, campus photo backgrounds
|   `-- util/             Time formatting, text limits
|
|-- data/
|   |-- config/           Validation of Remote Config values
|   |-- local/datastore/  DataStore persistence of progress and settings
|   |-- remote/firebase/  Firebase Auth, Firestore and Remote Config data sources
|   |-- repository/       Repository implementations
|   `-- sync/             Progress sync, conflict merging, transfer codes
|
|-- domain/
|   |-- config/           Gameplay balance values
|   |-- game/
|   |   |-- engine/       Game engine, overlap graph, completion timer
|   |   |-- model/        Board, tile, tray and game state models
|   |   |-- shuffle/      Board shuffling
|   |   `-- stress/       Stress System, Coffee and Music Recovery
|   |-- leaderboard/      Leaderboard models
|   |-- level/
|   |   |-- generator/    Procedural level generation
|   |   |-- model/        Level and Semester Week configuration
|   |   `-- solver/       Solvability validation
|   |-- progress/         Player progress, Personal Bests, transfer codes
|   |-- repository/       Repository interfaces
|   `-- settings/         Player settings
|
|-- feature/              Compose screens, UI state and ViewModels
|   |-- account/
|   |-- game/
|   |-- home/
|   |-- leaderboard/
|   |-- levelselect/
|   |-- result/
|   `-- settings/
|
|-- navigation/           Navigation graph and routes
|
|-- sensor/
|   |-- haptic/           Haptic feedback
|   |-- light/            Ambient light brightness control
|   |-- shake/            Shake detection
|   `-- tilt/             Tilt processing
|
`-- MainActivity.kt
```

### Package Responsibilities

- **app** — application-level setup and dependency wiring
- **core** — shared theme, UI components, audio and utilities
- **domain** — platform-independent gameplay rules, models, level generation, and repository contracts
- **data** — DataStore and Firebase data sources, repository implementations, and synchronisation
- **feature** — Jetpack Compose screens, UI state, events, and ViewModels organised by feature
- **navigation** — application navigation graph and routes
- **sensor** — sensor processing for Shake-to-Shuffle, Tilt-to-Peek and auto brightness, plus haptic feedback

## Getting Started

### Requirements

- Android Studio
- JDK 17
- Android SDK Platform 34
- An Android device or emulator running Android 8.0 (API 26) or later
- Git

### Clone the Repository

```bash
git clone https://github.com/YinggeSun/comp90018-deadline.git
cd comp90018-deadline
```

Open the project in Android Studio and allow Gradle to sync.

### Run the Application

1. Open the project in Android Studio.
2. Select an Android emulator or physical Android device.
3. Run the `app` configuration.

The Firebase configuration (`app/google-services.json`) is included, so the app connects to the
team's Firebase project with no extra setup. The game is fully playable offline. Without a network,
only the leaderboard, accounts and progress sync are unavailable.

A physical Android device is recommended for the sensor features (Shake-to-Shuffle, Tilt-to-Peek
and auto brightness), because emulators do not provide real motion or light readings.

## Testing

Commands are run from the repository root (use `gradlew.bat` on Windows).

| Check | Command |
|---|---|
| JVM unit tests | `./gradlew :app:testDebugUnitTest` |
| Kotlin formatting | `./gradlew ktlintCheck` |
| Debug build | `./gradlew :app:assembleDebug` |
| Android Lint | `./gradlew :app:lintDebug` |
| Instrumented tests | `./gradlew :app:connectedEmulatorAndroidTest` |

- **JVM unit tests** (`app/src/test`) cover:
  - the game engine, Stress System, shuffling and undo
  - level generation and the solver
  - repositories, persistence and sync
  - ViewModels
  - the sensor signal processing (shake and tilt)
- **Instrumented tests** (`app/src/androidTest`) cover Compose UI screens, navigation, end-to-end
  acceptance flows, and the Firestore data sources and Security Rules. They run on an Android
  Emulator in the isolated `emulator` build type, against the local Firebase Emulator Suite
  (`firebase emulators:start --only auth,firestore --project demo-deadline`), never against
  production data. See [scripts/README.md](scripts/README.md) and
  [Android test isolation](docs/issue-65-android-isolation.md).
- **Continuous integration:** GitHub Actions runs ktlint, the JVM unit tests, a debug build and
  Android Lint on every pull request to `main`. See [CI and quality checks](docs/ci-quality.md).
- **Test documentation:**
  - [game engine tests](docs/testing-game-engine.md)
  - [level generation tests](docs/testing-level-generation.md)
  - [persistence QA](docs/qa/issue-43-persistence.md)
  - [Compose UI and navigation QA](docs/qa/issue-44-compose-navigation.md)
  - [guest account recovery QA](docs/qa/issue-65-guest-recovery.md)

## Image Credits

The app uses photos of the University of Melbourne Parkville campus as backgrounds. University logos and crests have been cropped out. The app icon is an original vector drawing.

| File | Photo | Used for |
|---|---|---|
| `bg_home.webp` | Old Arts clock tower reflected in the pond | Home |
| `bg_level_1.webp` | Sandstone heritage building | Level 1 (Weeks 1–2) |
| `bg_level_2.webp` | Arches outside the Raymond Priestley Building | Level 2 (Weeks 3–4) |
| `bg_level_3.webp` | Arched walkway | Level 3 (Weeks 5–6) |
| `bg_level_4.webp` | Sports centre and running track | Level 4 (Weeks 7–8) |
| `bg_level_5.webp` | Library staircase at night | Level 5 (Weeks 9–10) |
| `bg_level_6.webp` | Old Arts clock tower | Level 6 (Weeks 11–12) |

The images are in `app/src/main/res/drawable-nodpi/`.

## Audio Credits

The background music and sound effects are CC0 1.0 assets from OpenGameArt. Creators, sources and
file checksums are listed in [docs/AUDIO_CREDITS.md](docs/AUDIO_CREDITS.md).

## Documentation

- [User accounts](docs/user-accounts.md): account lifecycle, Firestore schema, Firebase Console setup
- [Firestore Security Rules](docs/firestore-security.md)
- [Remote Config](docs/remote-config.md): tunable gameplay balance keys
- [CI and quality checks](docs/ci-quality.md)
- [Contributing guide](CONTRIBUTING.md)

## Team

COMP90018  
Group **T01/05 - 03**

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the branch naming convention, commit style, pull request workflow, and review requirements.
