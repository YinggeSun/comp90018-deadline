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
