# Issue #65 — Local emulator execution, 2026-10-12

Branch: `feature/issue-65-user-auth`. No commits, pushes, PRs, Firebase Rules deployments, or production Firebase requests were made during this task.

## Actual execution

From `C:\Users\xiaot\AndroidStudioProjects\Deadline` in PowerShell:

```powershell
& 'C:\Users\xiaot\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices -l
python scripts/test_firebase_emulator_rules.py 2>&1 | Tee-Object -FilePath build/issue65-emulator-tests.log
exit $LASTEXITCODE
```

The Android inventory was empty both before and after the host tests. No instrumentation task was launched; no compilation result is counted as execution.

The Python runner completed with exit code 0: **125 checks passed, 0 failed, 0 execution errors**. These are newly added host REST integration checks against actual emulator rules, not executions of the Kotlin test methods.

| Executed group | Passed | Coverage |
| --- | ---: | --- |
| Profile | 46 | Owner create/read/update; registered and anonymous cross-account reads/writes denied; signed-out access denied; forged emails, extra/missing fields, invalid nicknames/account types/timestamps denied; collection listing and deletion denied; server timestamp and nickname updates accepted |
| Auth with Firestore | 3 | Anonymous linking retains progress access; linked identity creates registered profile; password login retains profile access |
| Progress | 24 | Anonymous owner create/read, improvements accepted; cross-account access/create, signed-out access, listing, regression/deletion, malformed fields and implausible best times denied |
| Leaderboard | 30 | Valid creation, signed-in listing, faster update, first/last level time floors; cross-account/signed-out writes, equal/slower times, malformed fields, unknown levels and deletion denied |
| Unmatched paths | 2 | Reads and writes denied |
| Transfer | 20 | Valid creation and another signed-in user's known-code read; invalid fields/codes/expiry, signed-out access, listing/update/delete denied; expired code denied after initially successful read |

Additional successful assertions verify unchanged UID across linking/login, unchanged profile creation time, preserved last-login time during nickname edits, and matching server last-login/update timestamps after login updates. They are not included in the 125 HTTP permission-check count.

Artifacts:

- `build/issue65-emulator-tests.log`: individual check outcomes.
- `build/issue65-emulator-results.json`: machine-readable results.
- `build/issue65-rules-coverage.json`: actual loaded rules and emulator evaluation coverage after execution.
- `scripts/test_firebase_emulator_rules.py`: repeatable, standard-library-only host runner.

## Existing Android tests inspected but NOT executed

| Suite | Test methods | Initialization |
| --- | ---: | --- |
| `FirestoreProfileEmulatorTest` | 3 | `app()` calls shared `FirebaseEmulator.newApp`; named Auth/Firestore instances injected into profile/auth/progress data sources |
| `FirestoreRulesTest` | 30 | Each `Player` calls shared `newApp`; raw Firestore operations, anonymous or signed-out clients |
| `FirestoreLeaderboardEmulatorTest` | 8 | Each `Player` calls shared `newApp`; named Auth/Firestore instances injected into repository/data source |
| `FirestoreProgressSyncEmulatorTest` | 3 | `setUp()` calls shared `newApp`; named clients injected into sync manager and repositories |
| `FirestoreTransferEmulatorTest` | 4 | Each `Device` calls shared `newApp`; named clients injected into transfer/sync services |
| **Total** | **48** | **Not executed** |

The shared helper explicitly constructs FirebaseOptions with project `demo-deadline`, application ID `1:1:android:1`, API key `emulator-only`, and a unique `player-<UUID>` app name. It calls Auth `useEmulator("10.0.2.2", 9099)` and Firestore `useEmulator("10.0.2.2", 8080)` before returning each app. Tests consistently obtain clients from those named apps; no suite requires a different project ID. All five suites check emulator reachability. Four clear the entire local demo Firestore database in setup; the profile suite uses unique users instead. Leaderboard legacy-data fixtures use emulator admin writes; ordinary rule assertions do not.

## Confirmed running configuration and mismatches

- Local hub `http://127.0.0.1:4400/emulators` reported Auth 9099 and Firestore 8080. The startup log confirms CLI 15.33.0, Java 21, project `demo-deadline`, and this checkout's `firestore.rules` path.
- The runner checked that the rules returned by the emulator coverage endpoint matched the local rules file **byte for byte**, before and after execution. SHA256: `6bd195404f75958b724f51bc1ff96733f816ba827231648f4df209747addeae4`. It did not upload or replace any rules.
- Every Auth emulator ID token used by the runner had its audience and issuer checked for `demo-deadline`.
- Android helper address `10.0.2.2` is the Android virtual-device host alias. The host runner correctly uses `127.0.0.1`; a physical phone cannot use the existing helper address unchanged.
- `app/google-services.json` configures the default application for `comp90018-deadline`, not `demo-deadline`. This does not affect the named test clients or the host runner, but it creates the instrumentation startup risk below.
- No `.firebaserc` exists. The running emulator's explicit `--project demo-deadline` flag and the explicit client project IDs agree; no project mismatch was observed.

## Instrumentation blockers and production safety

1. `adb devices -l` returned no connected emulator or physical device.
2. The standard `AndroidJUnitRunner` uses the target manifest's `DeadlineApp`. Its `onCreate()` invokes background sign-in, progress sync, and Remote Config refresh. `AppContainer` obtains default Firebase clients configured by `app/google-services.json`. Therefore the complete existing instrumentation process cannot be certified emulator-only merely because its named test clients use `useEmulator`. **Do not launch these suites against a device until test application/startup isolation is configured and verified**, including default Firebase initialization and background services. A device alone does not resolve this safety blocker.

The host runner avoids the Android application entirely. All HTTP calls pass through one guard restricted to `http://127.0.0.1` on ports 4400, 8080 and 9099. Proxies are disabled and redirects rejected. It uses only locally issued user ID tokens, never admin credentials or application-default credentials. Expected denials must be HTTP 403 with `PERMISSION_DENIED`; arbitrary failures cannot pass denial checks. Random test IDs avoid resetting existing data. Test accounts/documents remain in the disposable local emulators.

No Android configuration or application source was changed. Only the host test runner and this report were added. Existing staged changes and unrelated local edits were preserved.

Remaining unverified behavior includes Android SDK/repository integration, live listeners/ranking, transaction-based progress merging, transfer-service behavior and offline-cache expiry. The REST rules checks establish server permission behavior, not those client implementations. Existing JVM unit tests can execute without an Android device but cannot substitute for these emulator integration suites; no JVM rerun was needed for this host-only addition.

Firebase documents the coverage endpoint and initially loaded local rules in [Firestore rule testing](https://firebase.google.com/docs/firestore/security/test-rules-emulator), and cross-service project matching/local Auth tokens in [Authentication emulator setup](https://firebase.google.com/docs/emulator-suite/connect_auth).
