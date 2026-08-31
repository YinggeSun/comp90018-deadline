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

**Select an available tile → Add it to the Task Tray → Complete three-tile matches → Manage Stress and available items → Clear the level → Record and improve the Personal Best**

Players select available tiles from a layered board and move them into a seven-slot Task Tray. When three identical tiles are collected, they are automatically matched and removed.

## Core Features

Planned features include:

- Layered tile availability and overlap detection
- Seven-slot Task Tray
- Automatic three-tile matching
- Win and loss conditions
- Stress System
- Coffee Recovery
- Shake-to-Shuffle using Accelerometer and Gyroscope
- Undo
- Semester Week difficulty progression
- Fixed and procedurally generated levels
- Solvability validation
- Completion Timer
- Personal Best records
- Local persistence using DataStore
- Anonymous Firebase Authentication
- Cloud Firestore leaderboard
- Offline-first gameplay

## Tech Stack

- Kotlin
- Jetpack Compose
- Material Design
- MVVM
- StateFlow
- Android Accelerometer
- Android Gyroscope
- Android vibration / haptic feedback
- DataStore
- Firebase Authentication
- Cloud Firestore
- Gradle Kotlin DSL
- Git and GitHub

## Architecture

The application follows an MVVM-based architecture.

```text
User Input
    |
    v
Compose UI
    |
    v
GameViewModel
    |
    +------> Game Engine ------> Game State
    |
    +------> Repository -------> DataStore / Firebase

Sensor Input
    |
    v
Sensor Manager
    |
    v
GameViewModel
    |
    v
Game Engine
```

The project separates UI, gameplay logic, sensor processing, persistence, and cloud connectivity to keep components easier to develop, test, and integrate.

## Planned Project Structure

```text
app/src/main/java/com/comp90018/deadline/
|
|-- MainActivity.kt
|
|-- ui/
|   |-- navigation/
|   |-- home/
|   |-- levelselect/
|   |-- game/
|   |-- result/
|   |-- components/
|   `-- theme/
|
|-- game/
|   |-- engine/
|   |-- model/
|   |-- tray/
|   |-- level/
|   `-- stress/
|
|-- sensor/
|
|-- data/
|   |-- local/
|   |-- remote/
|   `-- repository/
|
`-- viewmodel/
```

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

For motion-sensor features, testing on a physical Android device is recommended.

## Testing

The project plans to include:

- Unit tests for core gameplay logic
- Integration tests for ViewModel, Game Engine, repositories, and persistence
- Jetpack Compose UI tests
- Physical-device testing for Accelerometer and Gyroscope interactions
- Usability and performance testing

## Team

COMP90018
Group **T01/05 - 03**

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the branch naming convention, commit style, pull request workflow, and review requirements.
