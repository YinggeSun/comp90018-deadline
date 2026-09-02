# Deadline!

**Deadline!** is a campus-themed casual tile-matching Android game developed for **COMP90018 Mobile Computing Systems Programming**.

## Project Information

- **Course:** COMP90018 Mobile Computing Systems Programming
- **Group:** T01/05 - 03
- **Platform:** Android
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM

## Project Overview

Deadline! turns familiar university experiences such as assignments, lecture notes, textbooks, code files, quizzes, coffee, deadlines, and semester stress into gameplay mechanics.

The core gameplay loop is:

**Select an available tile → Add it to the Task Tray → Complete three-tile matches → Manage Stress and recovery items → Clear the level → Record and improve the Personal Best**

Players select available tiles from a layered board and move them into a seven-slot Task Tray. When three identical tiles are collected, they are automatically matched and removed.

## Core Features

Planned features include:

- Layered tile availability and overlap detection
- Seven-slot Task Tray
- Automatic three-tile matching
- Win and loss conditions
- Stress System
- Coffee Recovery
- Undo
- Shake-to-Shuffle using Accelerometer and Gyroscope
- Tilt-to-Peek using device orientation
- Semester Week difficulty progression
- Fixed and procedurally generated levels
- Solvability validation
- Completion Timer
- Personal Best records
- Local persistence using DataStore
- Anonymous Firebase Authentication
- Cloud Firestore leaderboard
- Cross-device progress synchronisation
- Firebase Remote Config
- Offline-first gameplay

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- MVVM
- StateFlow
- Android SensorManager
- Accelerometer
- Gyroscope
- Rotation Vector Sensor
- Android haptic feedback
- DataStore
- Firebase Authentication
- Cloud Firestore
- Firebase Remote Config
- Gradle Kotlin DSL
- Git and GitHub

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
|-- app/
|   |-- AppContainer.kt
|   `-- DeadlineApp.kt
|
|-- core/
|   |-- dispatcher/
|   |-- theme/
|   |-- ui/
|   |   |-- components/
|   |   `-- modifier/
|   `-- util/
|
|-- data/
|   |-- local/
|   |   |-- datastore/
|   |   `-- model/
|   |
|   |-- remote/
|   |   |-- firebase/
|   |   `-- model/
|   |
|   |-- repository/
|   `-- sync/
|
|-- domain/
|   |-- game/
|   |   |-- engine/
|   |   |-- model/
|   |   |-- shuffle/
|   |   `-- stress/
|   |
|   |-- level/
|   |   |-- generator/
|   |   |-- model/
|   |   `-- solver/
|   |
|   |-- progress/
|   `-- repository/
|
|-- feature/
|   |-- game/
|   |-- home/
|   |-- leaderboard/
|   |-- levelselect/
|   |-- result/
|   `-- settings/
|
|-- navigation/
|
|-- sensor/
|   |-- haptic/
|   |-- shake/
|   `-- tilt/
|
`-- MainActivity.kt
```

### Package Responsibilities

- **app** — application-level setup and dependency wiring
- **core** — shared utilities, theme, UI components, and infrastructure
- **domain** — platform-independent gameplay rules, models, level generation, and repository contracts
- **data** — DataStore and Firebase data sources, repository implementations, and synchronisation
- **feature** — Jetpack Compose screens, UI state, events, and ViewModels organised by feature
- **navigation** — application navigation graph and routes
- **sensor** — motion sensor processing, Shake-to-Shuffle, Tilt-to-Peek, and haptic feedback

## Getting Started

### Requirements

- Android Studio
- Android SDK API 26 or later
- JDK supported by the project's Gradle configuration
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

A physical Android device is recommended for testing motion-sensor features such as Shake-to-Shuffle and Tilt-to-Peek.

## Testing

The project plans to include:

- JVM unit tests for core gameplay logic
- Unit tests for level generation and solvability validation
- Integration tests for ViewModels, Game Engine, repositories, and persistence
- Jetpack Compose UI tests
- Physical-device testing for sensor interactions
- Firebase and offline behaviour testing
- Usability testing
- Performance and recomposition testing

## Team

COMP90018  
Group **T01/05 - 03**

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the branch naming convention, commit style, pull request workflow, and review requirements.
