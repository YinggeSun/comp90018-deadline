# Issue #65: isolated Android instrumentation

Update: the startup blocker recorded below has been diagnosed and resolved. All 50 selected
tests now pass. See [the timeout diagnosis and final results](issue-65-startup-timeout-diagnosis.md).
The launcher now applies and cleans up a bounded Android network-policy exemption for the
isolated test package; all Firebase isolation guards remain enforced. The results below are
the historical first attempt.

## Setup

Instrumentation now targets the dedicated `emulator` build type. It installs as `com.comp90018.deadline.emulator`, with separate preferences and Firebase state from the normal application. Its checked-in Google Services fixture contains only project `demo-deadline`, app ID `1:1:android:1` and dummy key `emulator-only`.

The emulator manifest removes `FirebaseInitProvider`, Remote Config, Installations and ABT component registrations (including the KTX registrations). `EmulatorApplication` asserts that no Firebase app has initialized, checks both local service ports from the device, then creates the default demo app and routes Auth to `10.0.2.2:9099` and Firestore to `10.0.2.2:8080`. Only after this does it call `DeadlineApp.onCreate`, allowing the usual auth/progress background work to start. Remote Config is replaced with a no-network source returning local defaults; directly requesting the Remote Config SDK fails because its component is absent.

`EmulatorTestRunner` rejects a wrong application class or package before instantiation. All existing named clients continue through `FirebaseEmulator.newApp`; that factory now requires the isolated application, checks service reachability, and verifies project, dummy key, Firestore host and disabled TLS. Missing services fail tests instead of skipping. No endpoint falls back to production.

The only main-source changes are an overridable container factory and optional Remote Config source injection. Defaults retain the previous production behavior. The normal debug/release manifests and Google Services configuration are unchanged. Existing UI integration tests still receive a `DeadlineApp` subclass and real `AppContainer`; no account or progress logic was rewritten.

## Repeatable command (PowerShell, repository root)

Start the local Firebase emulators separately with explicit project `demo-deadline`. Connect a standard Android Emulator that can reach host ports 9099 and 8080 through `10.0.2.2`. Then:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
& .\scripts\run_firebase_android_tests.ps1 -Serial emulator-5554
```

The script checks device/host prerequisites, builds both APKs, verifies generated demo resources and merged application/test manifests, and invokes exactly:

```powershell
.\gradlew.bat :app:assembleEmulator :app:assembleEmulatorAndroidTest --console=plain
.\gradlew.bat :app:connectedEmulatorAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.comp90018.deadline.data.FirebaseIsolationTest,com.comp90018.deadline.data.FirestoreProfileEmulatorTest,com.comp90018.deadline.data.FirestoreRulesTest,com.comp90018.deadline.data.FirestoreLeaderboardEmulatorTest,com.comp90018.deadline.data.FirestoreProgressSyncEmulatorTest,com.comp90018.deadline.data.FirestoreTransferEmulatorTest' --console=plain
```

The script sets `ANDROID_SERIAL` for execution and restores it afterward. It does not change emulator endpoints, Windows firewall settings, rules or production data. Do not use old `connectedDebugAndroidTest` APKs or commands; instrumentation now targets `emulator`. Ordinary debug/release APKs are not isolated test APKs. The existing rules/leaderboard/progress/transfer suites clear **all local demo Firestore documents** in setup; do not run concurrently with other users of this disposable emulator database.

## Actual verification on 2026-10-12

- Connected device: `emulator-5554`, Pixel Fold API 35, Android 15.
- `assembleEmulator`, `assembleEmulatorAndroidTest`, `assembleDebug`, `processDebugMainManifest`, `processReleaseMainManifest`, and `:app:ktlintCheck`: passed. Final build/lint log: `build/issue65-isolation-verification.log`.
- Three merged-manifest contract checks passed: debug/release still use `DeadlineApp`, `FirebaseInitProvider` and Remote Config registration; emulator uses `EmulatorApplication` and excludes automatic initialization and Remote Config/Installations registrations.
- The repeatable script passed its host prerequisite, resource and manifest checks, then attempted instrumentation. **Startup failed closed before Firebase initialization**: `SocketTimeoutException` connecting from Android `10.0.2.16` to host `10.0.2.2:9099` after 2000 ms.
- **0 test methods executed; 0 passed; 0 method failures; 1 instrumentation startup failure.** A zero-method JUnit XML is not a successful test run. The Gradle instrumentation task failed.

| Selected suite | Methods | Result |
| --- | ---: | --- |
| FirebaseIsolationTest | 2 | Not executed |
| FirestoreProfileEmulatorTest | 3 | Not executed |
| FirestoreRulesTest | 30 | Not executed |
| FirestoreLeaderboardEmulatorTest | 8 | Not executed |
| FirestoreProgressSyncEmulatorTest | 3 | Not executed |
| FirestoreTransferEmulatorTest | 4 | Not executed |
| Total | 50 | Blocked at application startup |

`FirebaseIsolationTest` is compiled but remains unexecuted. It checks default and named app options, locally issued Auth token audience/issuer, Firestore settings and server reads/writes, unavailable Remote Config SDK, and successful local-default config refresh. The final config-refresh assertion was strengthened after the failed launch; the final test APK rebuilt successfully but was not relaunched while connectivity remained blocked.

Execution evidence: `build/issue65-android-emulator-tests.log`, `app/build/reports/androidTests/connected/emulator/index.html`, and the XML/crash report under `app/build/outputs/androidTest-results/connected/emulator/`. Old reports under `connected/debug` predate this task and are not evidence for these runs.

## Remaining blocker and limits

Windows loopback checks for 9099 and 8080 both succeeded after the failure, but device-to-host Auth connectivity did not. The exact network/firewall cause is not established. Restore Android Emulator access to the host services, then rerun the command; the reachability guard must stay enabled. No firewall modifications, alternate routing, relaxed checks or production fallback were attempted.

Until that succeeds, runtime initialization beyond the guard, the two routing tests and all 48 existing Firebase instrumentation tests remain unverified. The earlier 125 host REST checks are separate evidence and were not rerun or counted as Android tests. Normal debug was built, not launched. No production Firebase service was accessed by this attempt, and no rules were deployed, commits made, pushes performed or PRs created.
