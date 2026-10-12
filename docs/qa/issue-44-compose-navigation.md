# Issue #44: Compose UI and navigation acceptance

Review date: 2026-10-11. Base: main `cc292c92173f3329772ab61fac0329d0125134d3`. This change adds seven Android tests, improves scroll-aware navigation assertions, uses the production theme/Scaffold in navigation tests, and adds optional device screenshot capture. Production code is unchanged.

## Acceptance coverage

| Issue requirement | Automated evidence |
| --- | --- |
| Home | Title, Play/Leaderboard/Settings callbacks, Continue hidden/shown, destination routes; real MainActivity navigation and return to Home |
| Level Select | Unlocked selection passes the correct ID, locked cards cannot navigate, details/best-time display, Back; real generated Level 1 entry |
| Game interaction | Selectable/covered tiles, board-to-tray movement, Undo enabled state, real Undo restoration and Restart reset, loading/retry, compact layouts, board sizing and accessible touch targets |
| Result navigation | Win/loss presentation, outcome arguments including previous best, Retry/Next Level/Level Select/Home, finished-game removal, Back after Retry, direct Home-to-game fallback to Level Select |
| Settings | Switch state and row callbacks, nickname validation/save/failure retry, real storage round-trip; all five switches remain off after MainActivity recreation; system Back returns Home |

`AppNavHostTest` now contains 12 tests (five added), and `MainActivityAcceptanceTest` adds two real activity tests. The new navigation cases cover leaving an unfinished game, Back after Retry, loss arguments/retry, duplicate Settings navigation prevention, and reaching Level Select after a game opened directly from Home.

## Validation record

- Full Android regression: **126 passed**, zero failures/errors/skips, on an Android API 35 emulator with local Firebase Auth/Firestore emulators running for the SDK tests.
- Final production-theme navigation/activity verification in light mode: **14 passed**, zero failures/errors/skips.
- Dark mode at 200% system font scale: **23 passed**, zero failures/errors/skips, covering all 12 navigation tests, the two real-activity tests, settings persistence and eight transfer-section UI tests.
- Debug and Release JVM suites: **568 passed each**, zero failures/errors/skips. This branch is independent of the eight extra #43 tests proposed in PR #122.
- Debug/release builds, `ktlintCheck`, `lintDebug`, `lintRelease`: passed. Lint reports the existing 48 Debug / 35 Release warnings, with zero errors.

Full regression ran before the final test-only theme/Scaffold adjustment; all 14 affected navigation/activity tests were then rerun with that adjustment. Firebase tests use the local `demo-deadline` project. This is not a production deployment audit.

## Reproduction

Use JDK 17, Android SDK, and a connected emulator. For the full Android suite, start the local Firebase emulator suite first:

```powershell
firebase emulators:start --only auth,firestore --project demo-deadline
.\gradlew.bat connectedDebugAndroidTest --console=plain --max-workers=2
.\gradlew.bat testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease ktlintCheck lintDebug lintRelease --console=plain --max-workers=2
```

Optional screenshots can be enabled on the navigation and real-activity tests:

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.comp90018.deadline.navigation.AppNavHostTest,com.comp90018.deadline.integration.MainActivityAcceptanceTest' '-Pandroid.testInstrumentationRunnerArguments.qaScreenshotPrefix=issue44' --console=plain --max-workers=2
adb pull /data/local/tmp/issue44-home.png
adb pull /data/local/tmp/issue44-level-select.png
adb pull /data/local/tmp/issue44-game.png
adb pull /data/local/tmp/issue44-settings.png
adb pull /data/local/tmp/issue44-settings-recreated.png
adb pull /data/local/tmp/issue44-result-win.png
adb pull /data/local/tmp/issue44-result-loss.png
```

Screenshots are captured through Android UiAutomation. Home/Level Select/Game/Settings use the unchanged MainActivity and generated game boards. Result screenshots use the same production composables/theme within the navigation test: the win uses a deterministic six-tile board, and the loss injects a game outcome to isolate navigation. They are not evidence that a generated board was manually won/lost. Settings are restored in teardown.

## Manual Android Studio verification

1. Open this checkout in Android Studio, select JDK 17 for Gradle, and launch the app on an emulator. Test both a fresh installation and a device with saved progress: Continue should match the available progress.
2. Home: open Play, Leaderboard and Settings, and use both the toolbar Back button and Android system Back to return.
3. Level Select: verify locked cards stay disabled; open Level 1. Tap an uncovered tile, check it moves to the tray, use Undo to restore it, and Restart to clear the tray and reset the run. System Back should return to Level Select without a Result screen.
4. Complete a board by matching sets of three. Verify the win page's time/best and each action: Retry, Next Level, Level Select, Home. After Retry or Next Level, Back must not return to the previous Result or finished game. For a loss, fill the tray with unmatched tiles; Next Level should be absent and Retry should open the same level.
5. Settings: toggle Background Music, Sound Effects, Vibration, Shake to shuffle and Tilt to peek; save a valid nickname; try a blank nickname; leave and re-enter. Recreate the activity or rotate the device and confirm saved settings persist. Restore preferences afterwards.
6. Repeat page navigation in dark mode with 200% font size. Scroll long screens to reach the nickname, transfer section, result actions and game controls; content outside the viewport must remain reachable. Small/short game layout assertions are also covered by existing Compose layout tests.

## Decision boundary

All five requirements in #44 are covered and pass; no blocking defect was found in this scope. Recommend merging after CI passes, then closing #44. Remote CI is not independently verified by this local validation. Physical sensor calibration and performance benchmarks remain under #45 and #48. Activity recreation is tested; manual rotation and screen-reader exploration are additional checks, not claimed as completed real-device acceptance here.
