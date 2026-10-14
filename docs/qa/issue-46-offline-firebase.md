# Issue #46: Offline and Firebase integration acceptance

Base: main `5a23889d7b37d945a2ac512aacbf7a0a32571e93`, including PR #136. Reviewed on 2026-10-14 (Australia/Sydney). This change adds six Android integration/acceptance tests and disposable `NetworkWatcher` registrations. Gameplay, UI and Firestore rules are unchanged; the Application's progress-sync observer retains its existing process lifetime.

## Acceptance coverage

| Requirement | Evidence |
| --- | --- |
| Online → Offline → Play → Complete → Reconnect | `OfflineGameAcceptanceTest`: authenticates and syncs online, enables airplane mode/disables Wi-Fi, verifies the device has no active network, plays and wins through the production navigation/engine/completion recorder, then reconnects |
| Local completion is retained while offline | UI test verifies completion, unlocked week, local modification and stable UID; `OfflineFirebaseIntegrationTest` closes/reopens a disk-backed DataStore and checks the saved progress is unchanged |
| Automatic reconnect progress sync | UI test independently observes the production `NetworkWatcher`, requiring a new callback after reconnection, and reads `progress/{uid}` with `Source.SERVER` until it matches local progress. It does not invoke manual sync after reconnect. Source review confirms `DeadlineApp` starts `AppContainer.startProgressSync` |
| Network observer cleanup | `NetworkWatcherLifecycleTest` closes one registration twice, reconnects the real device network, verifies no further events on that registration, and verifies another registration on the same watcher remains active |
| Queued leaderboard score | SDK test requires `SubmitResult.Queued`, reenables Firestore networking, awaits acknowledged pending writes, verifies the uploaded time and duplicate submission behavior; UI test independently verifies the queued score on the server and rendered ranking after real network loss |
| Offline leaderboard/cache recovery | The same SDK-backed repository listener must emit Ranked → Offline with cached entries → Ranked containing the improved time |
| Server refusal and retry | Blank nickname is rejected by real emulator rules; a valid nickname succeeds using the same repository |
| Authentication service unavailable | A named emulator-only Auth client points temporarily to an unavailable local port. Sign-in fails without producing a UID, leaderboard exposes Offline, restoring the Auth endpoint permits retry and subsequent calls retain the recovered identity |
| Invalid credentials and retry | Real Auth emulator rejects an incorrect password while preserving the Guest UID; valid retry reaches the expected registered UID with storage ready |

Existing full-suite coverage also verifies security rules, malformed ranking records, cross-user isolation, profile/progress/transfer operations, local persistence, account recovery and UI navigation.

## Important test boundaries

- All SDK calls target local `demo-deadline` emulators. The isolated emulator build and test runner fail closed if the required local services are missing; they do not fall back to production Firebase.
- The UI test injects only the existing deterministic six-tile `TestLevels` source. Gameplay, UI, completion recording, local storage, network callbacks and Firebase clients are production implementations. It does not inject a win or fake a cloud response.
- The fixture waits until the production game HUD's actual elapsed clock reaches at least five seconds, rather than sleeping for 4.6 host seconds. The server enforces a 4.5-second minimum for the production 18-tile Level 1. Quicker fixture wins are correctly refused and are not an application defect.
- Firestore `disableNetwork()` is used to test cached listeners and queued writes. It is not treated as proof that every transaction RPC is offline; device network loss is verified separately by the UI test.
- Every SDK test uses a new named client and resets only the demo emulator's Firestore. The UI test owns a fresh Guest identity in the isolated test app. Its cleanup scope begins before setup changes settings/registers callbacks. Extra network registrations are closed, Wi-Fi/airplane settings and preferences are restored using nested `finally` blocks, including setup/assertion failures; temporary DataStore resources are cleaned up.
- This is online-to-offline acceptance on an API 35 emulator, not a production deployment or physical-device certification. The emulator build requires online local services at process startup, so these tests do not certify an offline cold launch of the release APK.

## Reproduction

Use JDK 17, Android SDK, and one running Android emulator with working Wi-Fi. Reserve local port 9100 (Auth outage fixture); it must not have an Auth emulator listening. Start local services from the repository so they load its rules:

```powershell
firebase emulators:start --only auth,firestore --project demo-deadline
```

Run the focused suite:

```powershell
.\gradlew.bat connectedEmulatorAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.comp90018.deadline.data.OfflineFirebaseIntegrationTest,com.comp90018.deadline.integration.OfflineGameAcceptanceTest,com.comp90018.deadline.data.sync.NetworkWatcherLifecycleTest' --console=plain --max-workers=2
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

- Revised focused suite: **6 passed**, zero failures/errors/skips.
- Debug and Release JVM suites: **644 passed each**, zero failures/errors/skips.
- Full combined Android regression: **166 passed**, zero failures/errors/skips, on API 35 with disposable local Firebase Auth/Firestore emulators. This includes #136's performance/stability tests, all five original #46 tests and the new watcher-lifecycle test.
- Debug/Release/Emulator/Benchmark target and test/benchmark-driver builds, Ktlint, Debug/Release Lint: passed. Debug Lint has 0 errors and the same 38 warnings as the main/#136 baseline.
- Both lifecycle and reconnect negative controls failed at the intended assertions; restored-source focused/full runs then passed. Neither failing mutation was committed.

## Review feedback addressed

1. `NetworkWatcher.start` returns an individually owned, idempotent `AutoCloseable` registration. Closing it unregisters the Android callback. The UI test closes its extra observer in cleanup; the Application-owned observer intentionally stays registered for the process lifetime. `AppContainer.startProgressSync` preserves its Unit return contract.
2. The reconnect baseline is captured **after** `activeNetwork == null`. The test also requires that count to remain unchanged throughout offline play before accepting a new event after reconnection.
3. The anti-cheat wait observes the game's elapsed timer reaching five seconds. Authentication/setup, settings changes, observer registration, initial callback waiting and Compose content setup all lie inside the cleanup scope.
4. The branch includes the latest main, including #136's performance changes, so the full regression checks their combination with offline acceptance.

The UI observer uses the same production implementation as `AppContainer`; the closeable registration preserves that coverage while giving shorter-lived consumers explicit cleanup.

## Independent review and negative control

Review found that an eventual cloud upload alone does not isolate the network callback: the background win task can also participate in recovery. The UI test now additionally requires the real production watcher to report a new connection event, independently of the upload assertion. Application startup wiring was checked in `DeadlineApp` and `AppContainer`.

Two local-only negative controls verify the strengthened assertions:

- Replacing registration disposal with a no-op caused `NetworkWatcherLifecycleTest` to fail: closed observer event count expected 1, actual 2 after reconnection.
- Delivering the initial connection notification but suppressing subsequent notifications caused the revised UI acceptance test to fail specifically at the post-disconnection reconnect event assertion (15-second timeout), after completing offline gameplay.

Source was restored after each experiment; neither mutation is part of this PR. Wi-Fi and airplane mode returned to their original values even after the expected failures. An initial attempt without a running emulator was excluded from negative-control evidence.

Raw revised-run logs and XML are retained locally under `C:\Users\admin\Desktop\90018\qa-tools\pr134-feedback-results` and `pr134-feedback-*.log`. Historical interrupted/stale-Auth-token runs are excluded from passing results; the stale-token case prompted the explicit QA-package cleanup instruction above.

## Manual cross-check

Start online, open a level, disconnect Wi-Fi/mobile data, then play and complete it. Check the win/result screen and local unlocked level; reconnect, return to Leaderboard and select the completed level (the default tab may be the newly unlocked next level). Verify the result uploads and is highlighted as yours. Repeat with a previously loaded ranking to inspect the cached offline banner. Try an incorrect password and then a valid one; check that a rejected login did not switch accounts or lose the Guest's progress.
