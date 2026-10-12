# Issue #65 — Android startup timeout resolved

Verified on 2026-10-12, branch `feature/issue-65-user-auth`, device `emulator-5554`, Android API 35.

## Confirmed cause

Android's per-UID **background network firewall** blocked the isolated instrumentation process. During `EmulatorApplication.onCreate`, Android classified the process as cached-empty (`CEM`), with `blocked=APP_BACKGROUND`, `allowed=NONE`, `effective=APP_BACKGROUND`. The ADB shell UID was allowed. Successful shell probes therefore did not establish application connectivity.

Evidence and controlled comparison:

1. The installed APK's package dump showed `android.permission.INTERNET: granted=true`. The test APK runs instrumentation inside the target app process; its own manifest not declaring INTERNET was not the failure.
2. Shell `nc` connected to both ports; the same binary under `run-as com.comp90018.deadline.emulator` timed out on both. Binding either `10.0.2.15` or `10.0.2.16` as the source did not change this UID-dependent result.
3. The instrumented app logged `activeNetwork=null`, cleartext permitted for `10.0.2.2`, and independent Auth/Firestore socket timeouts at approximately 2000 ms. A live policy snapshot during startup showed `effective=APP_BACKGROUND`; the app was not merely blocked after crashing.
4. A 60-second temporary exemption for **only this test package** changed its effective blocked reasons to `NONE`. Both app-UID probes immediately succeeded. Removing the exemption made the Auth probe time out again.
5. With the scoped exemption during instrumentation, the unchanged startup socket limits succeeded in **4 ms for Auth and 3 ms for Firestore**, on Wi-Fi with source `10.0.2.16`. All 50 tests subsequently passed.

This establishes the Android UID policy as the cause of this failure, rather than missing INTERNET permission, manifest cleartext configuration, Wi-Fi versus Ethernet selection, Windows port availability, or a too-short timeout. No global data-saver, device-idle, restricted-networking or power-save mode was enabled. Those global flags being off does not disable the separate background firewall. [AOSP's network-policy implementation](https://android.googlesource.com/platform/frameworks/base/+/0ab54d6c305bce6d850ac97515afaaad95fc8b6c%5E%21/) documents this background-state restriction.

The executor was running the sockets: it returned `ExecutionException` wrapping their failures, not its own six-second `TimeoutException`. Native `nc` under the app UID reproduced the issue without any executor. The sockets succeeded under the exemption without changing executor structure or timeouts.

## Focused changes

- `EmulatorApplication.kt`: verifies installed INTERNET permission; logs UID, active-network/policy information, cleartext policy, both socket outcomes and elapsed times. Both endpoints are diagnosed even if the first fails. The original 2000 ms per-socket and 6-second future limits remain. Firebase initialization and application background startup still require both probes to pass.
- `run_firebase_android_tests.ps1`: after existing demo resource/manifest checks, installs the isolated APK and verifies its granted INTERNET permission. It grants a temporary Android background-network exemption to `com.comp90018.deadline.emulator` for at most ten minutes, then removes it in `finally`. The duration bounds the exemption if the host process is interrupted. It retains the isolated APKs after Gradle execution so cleanup can resolve the package. Cleanup was verified: `cmd deviceidle tempwhitelist` returned an empty list after this run.

No normal debug/release code or configuration was changed in this diagnosis. No Windows Firewall change, endpoint substitution, timeout increase, business-logic change or removal of the isolation guard was needed. The exemption affects Android's ability to send packets; it does not change demo credentials, service routing or Firebase permissions. This harness does not test Android background-network restrictions themselves.

## Commands

From the repository root in PowerShell (SDK path on this machine):

```powershell
$adb='C:\Users\xiaot\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5554 install -r app/build/outputs/apk/emulator/app-emulator.apk
& $adb -s emulator-5554 shell dumpsys package com.comp90018.deadline.emulator
& $adb -s emulator-5554 shell dumpsys netpolicy
& $adb -s emulator-5554 shell dumpsys network_management
& $adb -s emulator-5554 shell dumpsys connectivity
& $adb -s emulator-5554 shell dumpsys activity processes
& $adb -s emulator-5554 shell cmd appops get com.comp90018.deadline.emulator
& $adb -s emulator-5554 shell ip rule
& $adb -s emulator-5554 shell ip route show table all
& $adb -s emulator-5554 shell nc -z -w 5 10.0.2.2 9099
& $adb -s emulator-5554 shell nc -z -w 5 10.0.2.2 8080
& $adb -s emulator-5554 shell run-as com.comp90018.deadline.emulator /system/bin/nc -z -w 5 10.0.2.2 9099
& $adb -s emulator-5554 shell run-as com.comp90018.deadline.emulator /system/bin/nc -z -w 5 10.0.2.2 8080
```

The controlled comparison used `cmd deviceidle tempwhitelist -d 60000 com.comp90018.deadline.emulator`, repeated the two `run-as` probes with `-w 3`, removed the exemption with `cmd deviceidle tempwhitelist -r com.comp90018.deadline.emulator`, and repeated Auth's probe. Capturing policy during startup used this direct guarded launch:

```powershell
& $adb -s emulator-5554 shell am instrument -w -e class com.comp90018.deadline.data.FirebaseIsolationTest com.comp90018.deadline.emulator.test/com.comp90018.deadline.testing.EmulatorTestRunner
```

That diagnostic launch, without the exemption, crashed before running any methods. Direct `am instrument` can exit 0 even when its output says `Process crashed`; use the validated Gradle harness for normal runs.

Successful repeatable execution and lint:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
& .\scripts\run_firebase_android_tests.ps1 -Serial emulator-5554
.\gradlew.bat :app:ktlintCheck --console=plain
```

The script builds `:app:assembleEmulator :app:assembleEmulatorAndroidTest`, applies the temporary package exemption, and invokes:

```powershell
.\gradlew.bat :app:connectedEmulatorAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.comp90018.deadline.data.FirebaseIsolationTest,com.comp90018.deadline.data.FirestoreProfileEmulatorTest,com.comp90018.deadline.data.FirestoreRulesTest,com.comp90018.deadline.data.FirestoreLeaderboardEmulatorTest,com.comp90018.deadline.data.FirestoreProgressSyncEmulatorTest,com.comp90018.deadline.data.FirestoreTransferEmulatorTest' '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' --console=plain
```

## Actual results

| Suite | Executed | Passed | Failed/skipped |
| --- | ---: | ---: | ---: |
| FirebaseIsolationTest | 2 | 2 | 0 |
| FirestoreProfileEmulatorTest | 3 | 3 | 0 |
| FirestoreRulesTest | 30 | 30 | 0 |
| FirestoreLeaderboardEmulatorTest | 8 | 8 | 0 |
| FirestoreProgressSyncEmulatorTest | 3 | 3 | 0 |
| FirestoreTransferEmulatorTest | 4 | 4 | 0 |
| **Total** | **50** | **50** | **0** |

JUnit XML: tests=50, failures=0, errors=0, skipped=0, test time=24.956 seconds. Gradle instrumentation build succeeded in 40 seconds. Kotlin lint passed. Earlier diagnostic launches executed zero methods and are not counted as passes.

The passing isolation tests verify default and named apps use `demo-deadline`, dummy credentials, Auth tokens for the demo project, Firestore host `10.0.2.2:8080` without TLS, real server reads/writes, unavailable Remote Config SDK and local-default configuration refresh. Automatic Firebase initialization and Remote Config/Installations components remain absent from the emulator manifest. There is no production fallback.

Evidence in `build/`: `issue65-installed-package.txt`, `issue65-netpolicy-during-startup.txt`, `issue65-firewall-during-startup.txt`, `issue65-policy-temporary-exemption.txt`, `issue65-startup-probes.txt`, `issue65-startup-final.txt`, `issue65-corrected-instrumentation.log`, `issue65-exemption-cleanup.txt`, `issue65-diagnostics-lint.log`. The final JUnit XML and HTML report are under `app/build/outputs/androidTest-results/connected/emulator/` and `app/build/reports/androidTests/connected/emulator/`.

No remaining blocker for these 50 suites. The exemption expires after ten minutes; a materially longer run would need an explicitly revised duration. Tests require the disposable local emulators and clear their demo Firestore data. Other UI suites were not run in this task. No production Firebase requests were made by these tests; no commit, push, rule deployment or PR was performed.
