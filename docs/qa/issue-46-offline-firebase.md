# Issue #46: Offline and Firebase integration acceptance

Base: main `df22fd9d52f2400617922915cb02d4468d9e7a8f`, including PR #129. Reviewed on 2026-10-13. This change adds five Android integration/acceptance tests; production code and Firestore rules are unchanged.

## Acceptance coverage

| Requirement | Evidence |
| --- | --- |
| Online → Offline → Play → Complete → Reconnect | `OfflineGameAcceptanceTest`: authenticates and syncs online, enables airplane mode/disables Wi-Fi, verifies the device has no active network, plays and wins through the production navigation/engine/completion recorder, then reconnects |
| Local completion is retained while offline | UI test verifies completion, unlocked week, local modification and stable UID; `OfflineFirebaseIntegrationTest` closes/reopens a disk-backed DataStore and checks the saved progress is unchanged |
| Automatic reconnect progress sync | UI test independently observes the production `NetworkWatcher`, requiring a new callback after reconnection, and reads `progress/{uid}` with `Source.SERVER` until it matches local progress. It does not invoke manual sync after reconnect. Source review confirms `DeadlineApp` starts `AppContainer.startProgressSync` |
| Queued leaderboard score | SDK test requires `SubmitResult.Queued`, reenables Firestore networking, awaits acknowledged pending writes, verifies the uploaded time and duplicate submission behavior; UI test independently verifies the queued score on the server and rendered ranking after real network loss |
| Offline leaderboard/cache recovery | The same SDK-backed repository listener must emit Ranked → Offline with cached entries → Ranked containing the improved time |
| Server refusal and retry | Blank nickname is rejected by real emulator rules; a valid nickname succeeds using the same repository |
| Authentication service unavailable | A named emulator-only Auth client points temporarily to an unavailable local port. Sign-in fails without producing a UID, leaderboard exposes Offline, restoring the Auth endpoint permits retry and subsequent calls retain the recovered identity |
| Invalid credentials and retry | Real Auth emulator rejects an incorrect password while preserving the Guest UID; valid retry reaches the expected registered UID with storage ready |

Existing full-suite coverage also verifies security rules, malformed ranking records, cross-user isolation, profile/progress/transfer operations, local persistence, account recovery and UI navigation.

## Important test boundaries

- All SDK calls target local `demo-deadline` emulators. The isolated emulator build and test runner fail closed if the required local services are missing; they do not fall back to production Firebase.
- The UI test injects only the existing deterministic six-tile `TestLevels` source. Gameplay, UI, completion recording, local storage, network callbacks and Firebase clients are production implementations. It does not inject a win or fake a cloud response.
- The fixture waits at least 4.6 seconds of real time before completion because the server enforces a 4.5-second minimum for the production 18-tile Level 1. Quicker fixture wins are correctly refused and are not an application defect.
- Firestore `disableNetwork()` is used to test cached listeners and queued writes. It is not treated as proof that every transaction RPC is offline; device network loss is verified separately by the UI test.
- Every SDK test uses a new named client and resets only the demo emulator's Firestore. The UI test owns a fresh Guest identity in the isolated test app. Wi-Fi/airplane settings are restored in `finally`; settings and temporary DataStore resources are cleaned up.
- This is online-to-offline acceptance on an API 35 emulator, not a production deployment or physical-device certification. The emulator build requires online local services at process startup, so these tests do not certify an offline cold launch of the release APK.

## Reproduction

Use JDK 17, Android SDK, and one running Android emulator with working Wi-Fi. Reserve local port 9100 (Auth outage fixture); it must not have an Auth emulator listening. Start local services from the repository so they load its rules:

```powershell
firebase emulators:start --only auth,firestore --project demo-deadline
```

Run the focused suite:

```powershell
.\gradlew.bat connectedEmulatorAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.comp90018.deadline.data.OfflineFirebaseIntegrationTest,com.comp90018.deadline.integration.OfflineGameAcceptanceTest' --console=plain --max-workers=2
```

Optional screenshots use `-Pandroid.testInstrumentationRunnerArguments.qaScreenshotPrefix=issue46`. The helper writes `issue46-offline-game.png`, `issue46-offline-win.png` and `issue46-reconnected-leaderboard.png` to `/data/local/tmp/` on the emulator; pull them with adb. Use a fresh isolated app install for a clean screenshot dataset.

Run all validation:

If the Auth emulator was restarted/reset while the isolated app retained its credentials, clear **only** the QA package first (`adb shell pm clear com.comp90018.deadline.emulator`). Old emulator refresh tokens are no longer valid after that server reset. Do not clear the production application package.

```powershell
.\gradlew.bat testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease assembleEmulator assembleEmulatorAndroidTest ktlintCheck lintDebug lintRelease --console=plain --max-workers=2
.\gradlew.bat connectedEmulatorAndroidTest --console=plain --max-workers=2
```

On emulators where background network restrictions interfere with the test package, temporarily exempt `com.comp90018.deadline.emulator` using `adb shell cmd deviceidle tempwhitelist -d 600000 com.comp90018.deadline.emulator` and remove the exemption after the run. If a run is externally interrupted, restore airplane mode/Wi-Fi before rerunning.

## Validation record

- Focused suite: 5 passed, zero failures/errors/skips.
- Debug and Release JVM suites: 641 passed each, zero failures/errors/skips.
- Debug/Release/Emulator builds, test APK, ktlint and Lint: passed. Lint: 0 errors and 28 warnings for each Debug/Release variant.
- Full Android regression: 158 passed, zero failures/errors/skips, on Android API 35 with the local Firebase Auth/Firestore emulators. The full suite includes the five new tests and ran after the final test-isolation change.

## Independent review and negative control

Review found that an eventual cloud upload alone does not isolate the network callback: the background win task can also participate in recovery. The UI test now additionally requires the real production watcher to report a new connection event, independently of the upload assertion. Application startup wiring was checked in `DeadlineApp` and `AppContainer`.

As a local-only negative control, `NetworkWatcher` was temporarily changed to deliver its initial connection notification but suppress subsequent notifications. The strengthened test failed specifically at the reconnect event assertion (15-second timeout). Production source was then restored; the mutation is not part of this PR. This demonstrates detection of a broken reconnect callback rather than just successful score upload.

After restoring production source, the strengthened focused suite passed all five tests. The complete 158-test Android suite was then repeated successfully from a clean QA-package state with zero failures/errors/skips. Builds, formatting, Lint and both 641-test JVM suites were also validated. An interrupted run and a run with stale credentials after an Auth emulator restart were excluded from these passing results; the latter prompted the explicit QA-package cleanup instruction above.

## Manual cross-check

Start online, open a level, disconnect Wi-Fi/mobile data, then play and complete it. Check the win/result screen and local unlocked level; reconnect, return to Leaderboard and select the completed level (the default tab may be the newly unlocked next level). Verify the result uploads and is highlighted as yours. Repeat with a previously loaded ranking to inspect the cached offline banner. Try an incorrect password and then a valid one; check that a rejected login did not switch accounts or lose the Guest's progress.
