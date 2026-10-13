# Contributing to Deadline!

This document defines the development workflow for the Deadline! project.

## Main Development Rule

The `main` branch should always contain a buildable and reasonably stable version of the application.

Do not develop features directly on `main`.

## Starting New Work

Before creating a new branch, update your local `main` branch:

```bash
git checkout main
git pull origin main
```

Create a branch for the specific feature or task:

```bash
git checkout -b feature/<feature-name>
```

Example:

```bash
git checkout -b feature/task-tray
```

## Branch Naming

Use branches that describe the work being completed.

### Feature

```text
feature/<feature-name>
```

Examples:

```text
feature/task-tray
feature/shake-detection
feature/stress-system
feature/leaderboard
feature/compose-navigation
```

### Bug Fix

```text
fix/<bug-name>
```

Examples:

```text
fix/tray-loss-condition
fix/shake-false-positive
```

### Tests

```text
test/<test-name>
```

Examples:

```text
test/game-engine
test/level-validator
```

### Documentation

```text
docs/<document-name>
```

Example:

```text
docs/update-readme
```

### Refactoring

```text
refactor/<area>
```

Example:

```text
refactor/sensor-processing
```

### Repository or Build Maintenance

```text
chore/<task-name>
```

Example:

```text
chore/update-gradle
```

Avoid long-lived personal branches such as:

```text
yingge-branch
my-work
hao-dev
final-version
```

Branches should represent a specific feature, bug, test, or development task.

## Commit Messages

Use short and descriptive commit messages.

Recommended prefixes:

```text
feat:      new functionality
fix:       bug fix
test:      tests
refactor:  code restructuring without changing intended behaviour
docs:      documentation
chore:     project configuration or maintenance
```

Examples:

```text
feat: implement task tray insertion
feat: add accelerometer shake detection
fix: process matches before loss detection
test: add tile availability tests
refactor: separate sensor logic from game engine
docs: update project setup instructions
chore: configure repository workflow
```

Avoid vague messages such as:

```text
update
fix
done
final
final2
changes
```

A commit should ideally represent one logical change.

## Development Workflow

After creating a feature branch, make the required changes and commit them:

```bash
git add .
git commit -m "feat: implement task tray"
```

Push the branch:

```bash
git push -u origin feature/task-tray
```

Then create a Pull Request on GitHub from the feature branch into `main`.

## Before Opening a Pull Request

Run the automated quality commands and inspect the PR's `Android quality` check.
See [CI quality checks](docs/ci-quality.md) for setup, reports, formatting baseline,
and the administrator steps to require the check before merging.

Check the following:

1. The project builds successfully.
2. Relevant tests pass.
3. The feature behaves as expected.
4. No local or sensitive files are included.
5. The branch contains only relevant changes.
6. The branch has been updated with recent changes from `main` where necessary.

## Pull Request Review

Each Pull Request should clearly explain:

- What was implemented or changed
- Why the change was needed
- How it was tested
- Any known limitations
- Any areas reviewers should inspect carefully

At least one other team member should review a Pull Request before it is merged into `main`.

## Updating a Feature Branch

If `main` changes while you are working:

```bash
git checkout main
git pull origin main
git checkout feature/<feature-name>
git merge main
```

Resolve any conflicts locally, test the project again, and push the updated branch.

## After Merging

After a Pull Request is merged, the feature branch can normally be deleted.

Before starting the next task:

```bash
git checkout main
git pull origin main
```

Then create a new branch for the next feature.

## Code Organisation

The project separates responsibilities into the following main areas:

```text
ui/          Jetpack Compose screens and reusable UI components
game/        Core gameplay logic and game models
sensor/      Accelerometer and Gyroscope processing
data/        DataStore, Firebase, and repository implementations
viewmodel/   MVVM state management and integration
```

UI code should not directly modify the Game Engine, DataStore, or Firebase.

The intended high-level flow is:

```text
Compose UI
    |
    v
ViewModel
    |
    +------> Game Engine
    |
    `------> Repository
```

Sensor events should also pass through the ViewModel rather than directly changing board state.

## Testing Expectations

Where appropriate, new functionality should include corresponding tests.

Examples include:

- Tile availability
- Task Tray capacity
- Three-tile matching
- Win and loss conditions
- Stress behaviour
- Undo
- Personal Best comparison
- Level generation and solvability
- ViewModel integration
- Compose UI interaction

Sensor functionality should also be tested on physical devices where possible.

## Files That Must Not Be Committed

Do not commit machine-specific or sensitive files such as:

```text
local.properties
build/
.gradle/
.idea/workspace.xml
```

Firebase or other credentials must also be handled carefully and should not be committed unless the team has explicitly confirmed that the file is intended and safe to store in the repository.

## Team Contributions

Summary of each member's contributions, compiled from the repository's pull requests, issues and
reviews as of 13 October 2026. Pull requests still in review are marked *(in review)*.

### Summary

| Member | GitHub | Main area | Merged PRs |
|---|---|---|---|
| Yingge Sun (Jacob Sun) | YinggeSun | Core game engine, audio, user accounts, project coordination, demo video | 9 |
| Hao Xu | Hao6657 | UI, screens and navigation | 14 |
| CheukChun Choi | Helzy087 | Level model and procedural generation | 11 |
| Yukun Li | YUKUN-LI1234 | Stress System, timer, final documentation | 6 |
| Zechen Yang | Young501 | Integration, CI, testing and code review | 10 |
| lbhadauria | lbhadauria | Data layer, persistence and Firebase | 9 |
| Qirui Zhang | zqrs23333 | Sensors and haptics | 2 |

The final report was written by the whole team.

### Yingge Sun (Jacob Sun) — core game engine and coordination

- Core game data models (#54) and the game engine base structure (#56)
- Overlap graph and tile availability (#57)
- Seven-slot Task Tray (#58) and automatic three-tile matching (#59)
- Win and loss conditions with restart (#60) and configurable undo (#61)
- Background music and sound effects with persistent audio settings (#109)
- Optional email / password accounts with Firestore user profiles and isolated Firebase tests (#126)
- Coordination:
  - hosted the team meetings
  - created the project's task list as GitHub issues (62 issues)
  - led task allocation
- Demo video

### Hao Xu — UI, screens and navigation

- App navigation (#63) and the shared Material 3 theme and UI components (#70)
- Game board rendering (#71), Task Tray and game HUD (#76)
- Home (#78), Level Select (#77), Result (#83), Settings (#90) and Leaderboard (#124) screens
- New Music, Exam, Presentation and Lecture Slide tiles (#102)
- Smaller tiles with readable layers (#106, #108)
- The six generated levels connected to Level Select and gameplay (#111)
- Campus background photos and app icon (#113)
- Final UI / UX and accessibility polish (#129, in review)

### CheukChun Choi — levels and procedural generation

- Level data model and configuration format (#55) and the initial fixed playable levels (#64)
- Semester Week difficulty progression (#67) and procedural tile generation (#68)
- Layered board generation (#72) and progressive multi-layer layouts (#99)
- Level solvability validator (#73), integrated with generation (#74)
- Tile variety and difficulty progression (#88, #105)
- Six two-week level configurations and a generation metadata fix (#98)

### Yukun Li — Stress System and final documentation

- Stress System rules (#65), reworked into time-based stress with Maximum Stress effects (#104),
  and a stress configuration fix (#121)
- Completion timer (#66)
- Coffee Recovery (#79) and Music Recovery with a temporary stress slowdown (#107)
- Final documentation:
  - system architecture diagram (#130, in review)
  - meeting minutes (#131, in review)
  - README update (#132, in review)
  - this contribution summary

### Zechen Yang — integration, CI, testing and code review

- Android CI quality checks: ktlint, unit tests, build and lint (#80)
- Test suites for the game engine (#81) and for level generation and the solver (#82)
- Repository and persistence acceptance tests (#122)
- Compose UI and navigation acceptance tests (#123)
- Integration into `main`:
  - restored the generation and solvability pipeline (#69, #75)
  - integrated DataStore persistence (#89)
  - brought in the Firestore security rules (#120)
  - removed an obsolete integration note (#114)
- Code review and merging: reviewed 64 and merged 54 of the team's pull requests
- Assigned the remaining testing work: physical-device sensor testing (#45), offline and Firebase
  testing (#46), and the performance and stability pass (#48)

### lbhadauria — data layer, persistence and Firebase

- Repository interfaces and progress domain models (#84)
- DataStore persistence of settings, nickname and progress (#85)
- Recording wins, Personal Bests and Semester Week unlocks (#86)
- Firebase project connection (#87) and anonymous Firebase sign-in (#115)
- Firestore leaderboard (#116)
- Hardened Firestore Security Rules with leaderboard result validation (#117)
- Cross-device progress sync with transfer codes (#118)
- Gameplay balance tuning through Firebase Remote Config (#119)

### Qirui Zhang — sensors and haptics

- Sensor integration (#62):
  - sensor infrastructure
  - Shake-to-Shuffle (accelerometer)
  - Tilt-to-Peek (rotation vector sensor)
  - haptic feedback
  - the lifecycle-aware sensor binder for the game screen
- Ambient light display adaptation (#128, after earlier attempts in #97, #103 and #127)
